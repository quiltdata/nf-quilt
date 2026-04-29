/* groovylint-disable Instanceof */
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
import java.nio.charset.StandardCharsets

@Slf4j
@CompileStatic
class QuiltParser {

    static final String SCHEME = 'quilt+s3'
    static final String SEP = '/'
    static final String PREFIX = SCHEME + '://'
    static final int MIN_SIZE = 2
    static final String NULL_BUCKET = 'nf-quilt-dev-null'

    public static final String P_CAT = 'catalog'
    public static final String P_FORCE = 'force'
    public static final String P_PATH = 'path'
    public static final String P_PKG = 'package'
    public static final String P_WORK = 'workflow'

    private final String bucket
    private final String packageName
    private final String workflowName
    private final String catalogName
    private String[] paths
    private String hash
    private String tag
    protected final Map<String,Object> options
    protected final Map<String,Object> metadata

    static QuiltParser forBarePath(String path) {
        return forUriString(PREFIX + path)
    }

    static QuiltParser forNullBucket() {
        return new QuiltParser(NULL_BUCKET, 'dev/null', '')
    }

    static QuiltParser forUriString(String uriString) {
        URI uri = new URI(uriString)
        return forURI(uri)
    }

    static QuiltParser forURI(URI uri) {
        log.debug("forURI[${uri.scheme}] for ${uri}")
        if (uri.scheme != SCHEME) {
            String msg =  "Scheme[${uri}] URI:${uri.scheme}] != SCHEME:${SCHEME}"
            throw new IllegalArgumentException(msg)
        }
        def options = parseQuery(uri.fragment)
        def metadata = parseQuery(uri.query)
        String pkg = options.get(P_PKG)
        String path = options.get(P_PATH)
        return new QuiltParser(uri.authority, pkg, path, options, metadata)
    }

    static String decode(String str) {
        return URLDecoder.decode(str, StandardCharsets.UTF_8)
    }

    static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8)
    }

    static Map<String, Object> parseQuery(String query) {
        if (!query) { return [:] } // skip for urls without query params
        def params = query.split('&')
        Map<String, Object> result = [:]
        params.each { param ->
            def keyValue = param.split('=')
            if (keyValue.size() == 2) {
                String key = decode(keyValue[0])
                String value = decode(keyValue[1])
                if (result.containsKey(key)) {
                    Object listVal = result[key]
                    if (listVal instanceof List<String>) {
                        listVal << value
                    } else {
                        result[key] = [listVal, value]
                    }
                } else {
                    result[key] = value
                }
            }
        }
        return result as Map<String, Object>
    }

    static String encodePair(String key, String value) {
        return "${encode(key)}=${encode(value)}"
    }

    static String unparseQuery(Map<String,Object> query) {
        if (!query) { return '' } // skip for urls without query params
        List<String> params = query.collect {  key, value ->
            if (value instanceof List) {
                value.collect { encodePair(key, it.toString()) }.join('&')
            } else {
                encodePair(key, value.toString())
            }
        }
        return params.join('&')
    }

    QuiltParser(
        String bucket,
        String pkg,
        String path,
        Map<String,Object> options = [:],
        Map<String,Object> metadata = [:]
    ) {
        this.bucket = bucket
        this.paths = path ? path.split(SEP) : [] as String[]
        this.packageName = parsePkg(pkg)
        this.workflowName = options.get(P_WORK)
        this.catalogName = options.get(P_CAT)
        this.options = options
        this.metadata = metadata
        // log.debug("QuiltParser[${bucket}] for ${packageName} in ${path}")
    }

    String parsePkg(String pkg) {
        if (!pkg) { return null }
        if (!pkg.contains('/')) {
            log.error("Invalid package[$pkg]")
            return null
        }
        if (pkg.contains('@')) {
            def split = pkg.split('@')
            hash = split[1]
            return split[0]
        }
        if (pkg.contains(':')) {
            def split = pkg.split(':')
            tag = split[1]
            return split[0]
        }
        String new_pkg = pkg
        String[] split = pkg.split(SEP)
        if (split.size() > MIN_SIZE) {
            List<String> head = split[0..1]
            List<String> tail = split[2..-1]
            new_pkg = head.join(SEP)
            paths += tail
        }
        return new_pkg
    }

    QuiltParser appendPath(String tail) {
        String path2 = [getPath(), tail].join(SEP)
        while (path2.startsWith(SEP)) {
            path2 = path2.substring(1)
        }
        return new QuiltParser(getBucket(), getPackageName(), path2, options)
    }

    QuiltParser dropPath() {
        String[] subpath = ((paths.size() > 1) ? paths[0..-2] : []) as String[]
        String path2 = subpath.join(SEP)
        return new QuiltParser(getBucket(), getPackageName(), path2, options)
    }

    QuiltParser lastPath() {
        String path2 = paths.size() > 0 ? paths[-1] : ''
        return new QuiltParser(getBucket(), getPackageName(), path2, options)
    }

    QuiltParser subPath(int beginIndex, int endIndex) {
        String path2 = path(beginIndex, endIndex)
        return new QuiltParser(getBucket(), getPackageName(), path2, options)
    }

    QuiltParser normalized() {
        boolean skip = false
        Collection<String> rpaths = paths.reverse().findAll { String x ->
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

        List<String> rnorms = new ArrayList(rpaths)
        String path2 = rnorms.reverse().join(SEP)
        return new QuiltParser(getBucket(), getPackageName(), path2, options)
    }

    QuiltID quiltID() {
        return QuiltID.fetch(getBucket(), getPackageName())
    }

    String quiltIDS() {
        return quiltID().toString()
    }

    String getBucket() {
        return bucket?.toLowerCase()
    }

    String getPackageName() {
        return packageName
    }

    String getWorkflowName() {
        return workflowName
    }

    String getCatalogName() {
        return catalogName
    }

    String getHash() {
        return hash
    }

    String getTag() {
        return tag
    }

    String getPath() {
        return paths.join(SEP)
    }

    Map<String,Object> getMetadata() {
        return metadata
    }

    String path(int beginIndex, int endIndex) {
        List<String> sub = paths[beginIndex..<endIndex]
        return sub.join(SEP)
    }

    String[] getPaths() {
        return paths
    }

    boolean hasPath() {
        return paths.size() > 0
    }

    boolean hasNullBucket() {
        return NULL_BUCKET == getBucket()
    }

    String getOptions(String key) {
        return options?.get(key) as String
    }

    String toPackageString(boolean forKey = false) {
        String str = "${getBucket()}"
        if (metadata && !forKey) {
            str += "?${unparseQuery(metadata)}"
        }
        if (packageName) {
            String pkg = packageName
            if (hash) { pkg += "@$hash" }
            if (tag) { pkg += ":$tag" }
            str += "#package=${pkg.replace('/', '%2f')}"
        }
        return str
    }

    String toString() {
        String str = toPackageString()
        if (!hasPath()) { return str }
        str += (packageName) ? '&' : '#'
        str += "path=${getPath().replace('/', '%2f')}"
        if (workflowName) {
            str += "&workflow=${workflowName}"
        }
        if (catalogName) {
            str += "&catalog=${catalogName}"
        }
        return str
    }

    String toUriString() {
        return PREFIX + toString()
    }

}
