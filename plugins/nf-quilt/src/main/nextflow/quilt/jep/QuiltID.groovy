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
class QuiltID {

    private static final String[] DEFAULT_PACKAGE = ['package', 'default']
    private static final Map<String,QuiltID> QIDS = [:]

    private final String bucket
    private final String pkgPrefix
    private final String pkgSuffix

    static QuiltID fetch(String bucket, String packageName) {
        if (!bucket) {
            log.error("null == QuiltID.fetch($bucket, $packageName)")
            return null
        }
        String pkgName = packageName
        if (!packageName || packageName.size() < QuiltParser.MIN_SIZE) {
            pkgName = DEFAULT_PACKAGE.join(QuiltParser.SEP)
            log.warn("QuiltID.fetch: setting missing package to $pkgName")
        }
        String[] split = pkgName.split(QuiltParser.SEP)
        if (split.size() < QuiltParser.MIN_SIZE || split[1].size() < QuiltParser.MIN_SIZE) {
            split += DEFAULT_PACKAGE[1] as String
            log.warn("QuiltID.fetch: setting missing suffix to $split[1]")
        }
        String key = "${bucket}/${split[0]}/${split[1]}"
        if (!QIDS.containsKey(key)) {
            QIDS[key] = new QuiltID(bucket, split[0], split[1])
        }
        return QIDS[key]
    }

    QuiltID(String bucket, String pkgPrefix, String pkgSuffix) {
        this.bucket = bucket
        this.pkgPrefix = pkgPrefix
        this.pkgSuffix = pkgSuffix
    }

    String toString() {
        return "${bucket}.${pkgPrefix}.${pkgSuffix}"
    }

}
