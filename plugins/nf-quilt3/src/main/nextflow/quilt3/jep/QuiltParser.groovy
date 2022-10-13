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

package nextflow.quilt3.jep
import nextflow.quilt3.nio.QuiltPath

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import java.text.SimpleDateFormat
import java.util.Date
import java.lang.ProcessBuilder

@Slf4j
@CompileStatic
class QuiltParser {
    public static final String SCHEME = 'quilt+s3'
    public static final String SEP = '/'
    public static final String PREFIX = SCHEME+'://'
    public static final int MIN_SIZE = 2

    public static final String P_PKG = 'package'
    public static final String P_PATH = 'path'

    private final String bucket
    private final String pkg_name
    private String[] paths
    private String hash
    private String tag
    //private final String catalog
    private final Map<String,Object> options

    static public QuiltParser ForBarePath(String path) {
        QuiltParser.ForUriString(PREFIX+path)
    }

    static public QuiltParser ForUriString(String uri_string) {
        URI uri = new URI(uri_string)
        QuiltParser.ForURI(uri)
    }

    static public QuiltParser ForURI(URI uri) {
        log.debug("ForURI[${uri.scheme}] for $uri")
        if (uri.scheme != SCHEME)
            throw new IllegalArgumentException("Scheme[$uri] URI:${uri.scheme}] != SCHEME:${SCHEME}")
        def options = parseQuery(uri.fragment)
        String pkg = options.get(P_PKG)
        String path = options.get(P_PATH)
        new QuiltParser(uri.authority, pkg, path, options)
    }

    static private Map<String,Object> parseQuery(String query) {
        if (!query) return [:]
        final queryParams = query?.split('&') // safe operator for urls without query params
        queryParams.collectEntries { param -> param.split('=').collect { URLDecoder.decode(it) }}
    }

    QuiltParser(String bucket, String pkg, String path, Map<String,Object> options = [:]) {
        this.bucket = bucket
        this.hash = "latest"
        this.paths = path ? path.split(SEP) : [] as String[]
        this.pkg_name = parsePkg(pkg)
        this.options = options
    }

    String parsePkg(String pkg) {
        if (! pkg) return null
        if (! pkg.contains('/')) {
            log.error("Invalid package[$pkg]")
        }
        if (pkg.contains('@')) {
            def split = pkg.split('@')
            this.hash = split[1]
            return split[0]
        }
        if (pkg.contains(':')) {
            def split = pkg.split(':')
            this.tag = split[1]
            return split[0]
        }
        String[] split = pkg.split(SEP)
        if (split.size() > MIN_SIZE) {
            String[] head = split[0..1]
            String[] tail = split[2..-1]
            pkg = head.join(SEP)
            this.paths += tail
        }
        pkg
    }

    QuiltParser appendPath(String tail) {
        String path2 = [path(),tail].join(SEP)
        new QuiltParser(bucket(), pkg_name(), path2, options)
    }

    QuiltParser dropPath() {
        String path2 = paths[0..-2].join(SEP)
        log.debug("dropPath: ${path()} -> ${path2}")
        new QuiltParser(bucket(), pkg_name(), path2, options)
    }

    QuiltParser lastPath() {
        String path2 = paths.size() > 0 ? paths[-1] : path()
        log.debug("lastPath: ${path()} -> ${path2}")
        new QuiltParser(bucket(), pkg_name(), path2, options)
    }

    QuiltID quiltID() {
        QuiltID.Fetch(bucket(), pkg_name())
    }

    String quiltIDS() {
        quiltID().toString()
    }

    String bucket() {
        bucket ? bucket.toLowerCase() : null
    }

    String pkg_name() {
        pkg_name
    }

    String hash() {
        hash
    }

    String tag() {
        tag
    }

    String path() {
        paths.join(SEP)
    }

    String[] paths() {
        paths
    }

    boolean hasPath() {
        paths.size() > 0
    }

    String options(String key) {
        options ? options.get(key) : null
    }

    String toPackageString() {
        String str = "${bucket()}"
        if ( pkg_name ) {
            String pkg = pkg_name
            if ( hash ) { pkg += "@$hash" }
            if ( tag ) { pkg += ":$tag" }
            str += "#package=${pkg.replace('/','%2f')}"
        }
        str
    }

    String toString() {
        String str = toPackageString()
        if (! hasPath() ) return str
        str += ( pkg_name ) ? "&" : "#"
        str += "path=${path().replace('/','%2f')}"
    }

    String toUriString() {
        PREFIX + toString()
    }


}
