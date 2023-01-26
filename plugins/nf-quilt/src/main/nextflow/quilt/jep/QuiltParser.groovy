/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nextflow.quilt.jep

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class QuiltParser {

    static final String SCHEME = 'quilt+s3'
    static final String SEP = '/'
    static final String PREFIX = SCHEME + '://'
    static final int MIN_SIZE = 2

    static final String P_PKG = 'package'
    static final String P_PATH = 'path'

    private final String _bucket
    private final String _packageName
    private String[] _paths
    private String _hash
    private String _tag
    private final Map<String,Object> options

    static QuiltParser forBarePath(String path) {
        return QuiltParser.forUriString(PREFIX + path)
    }

    static QuiltParser forUriString(String uri_string) {
        URI uri = new URI(uri_string)
        return QuiltParser.forURI(uri)
    }

    static QuiltParser forURI(URI uri) {
        log.debug("forURI[${uri.scheme}] for ${uri}")
        if (uri.scheme != SCHEME) {
            String msg =  "Scheme[${uri}] URI:${uri.scheme}] != SCHEME:${SCHEME}"
            throw new IllegalArgumentException(msg)
        }
        def options = parseQuery(uri.fragment)
        String pkg = options.get(P_PKG)
        String path = options.get(P_PATH)
        return new QuiltParser(uri.authority, pkg, path, options)
    }

    static Map<String,Object> parseQuery(String query) {
        if (!query) { return [:] } // skip for urls without query params
        final queryParams = query.split('&')
        return queryParams.collectEntries { param -> param.split('=').collect { URLDecoder.decode(it) } }
    }

    QuiltParser(String bucket, String pkg, String path, Map<String,Object> options = [:]) {
        this._bucket = bucket
        this._paths = path ? path.split(SEP) : [] as String[]
        this._packageName = parsePkg(pkg)
        this.options = options
    }

    String parsePkg(String pkg) {
        if (!pkg) { return null }
        if (!pkg.contains('/')) {
            log.error("Invalid package[$pkg]")
        }
        if (pkg.contains('@')) {
            def split = pkg.split('@')
            _hash = split[1]
            return split[0]
        }
        if (pkg.contains(':')) {
            def split = pkg.split(':')
            _tag = split[1]
            return split[0]
        }
        String new_pkg = pkg
        String[] split = pkg.split(SEP)
        if (split.size() > MIN_SIZE) {
            String[] head = split[0..1]
            String[] tail = split[2..-1]
            new_pkg = head.join(SEP)
            _paths += tail
        }
        return new_pkg
    }

    QuiltParser appendPath(String tail) {
        String path2 = [path(), tail].join(SEP)
        while (path2.startsWith(SEP)) {
            path2 = path2.substring(1)
        }
        return new QuiltParser(bucket(), packageName(), path2, options)
    }

    QuiltParser dropPath() {
        String[] subpath = ((_paths.size() > 1) ? _paths[0..-2] : []) as String[]
        String path2 = subpath.join(SEP)
        return new QuiltParser(bucket(), packageName(), path2, options)
    }

    QuiltParser lastPath() {
        String path2 = _paths.size() > 0 ? _paths[-1] : ''
        return new QuiltParser(bucket(), packageName(), path2, options)
    }

    QuiltParser subPath(int beginIndex, int endIndex) {
        String path2 = path(beginIndex, endIndex)
        return new QuiltParser(bucket(), packageName(), path2, options)
    }

    QuiltParser normalized() {
        boolean skip = false
        String[] rnorms = _paths.reverse().findAll { String x ->
            if (x == '..') {
                skip = true
                false
            } else if (skip) {
                skip = false
                false
            } else {
                true
            }
        }

        log.debug("normalized: ${_paths} -> ${rnorms}")
        String path2 = rnorms.reverse().join(SEP)
        log.debug("normalized: -> ${path2}")
        return new QuiltParser(bucket(), packageName(), path2, options)
    }

    QuiltID quiltID() {
        return QuiltID.fetch(bucket(), packageName())
    }

    String quiltIDS() {
        return quiltID().toString()
    }

    String bucket() {
        return _bucket?.toLowerCase()
    }

    String packageName() {
        return _packageName
    }

    String hash() {
        return _hash
    }

    String tag() {
        return _tag
    }

    String path() {
        return _paths.join(SEP)
    }

    String path(int beginIndex, int endIndex) {
        String[] sub = _paths[beginIndex..<endIndex]
        return sub.join(SEP)
    }

    String[] paths() {
        return _paths
    }

    boolean hasPath() {
        return _paths.size() > 0
    }

    String options(String key) {
        return options?.get(key)
    }

    String toPackageString() {
        String str = "${bucket()}"
        if (_packageName) {
            String pkg = _packageName
            if (_hash) { pkg += "@$_hash" }
            if (_tag) { pkg += ":$_tag" }
            str += "#package=${pkg.replace('/', '%2f')}"
        }
        return str
    }

    String toString() {
        String str = toPackageString()
        if (!hasPath()) { return str }
        str += (_packageName) ? '&' : '#'
        str += "path=${path().replace('/', '%2f')}"
        return str
    }

    String toUriString() {
        return PREFIX + toString()
    }

}
