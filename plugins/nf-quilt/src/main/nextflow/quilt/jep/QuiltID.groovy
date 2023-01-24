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
import nextflow.quilt.jep.QuiltParser

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class QuiltID {
    public static final String[] DefaultPackage=["null","default"]
    private static final Map<String,QuiltID> ids = [:]

    private final String bucket
    private final String pkgPrefix
    private final String pkgSuffix

    static public QuiltID fetch(String bucket, String packageName) {
        if (!bucket) {
            log.error "null == QuiltID.fetch($bucket, $packageName)"
            return null
        }
        if (!packageName || packageName.size()<QuiltParser.MIN_SIZE) {
            packageName = DefaultPackage.join(QuiltParser.SEP)
            log.warn "QuiltID.fetch: setting missing package to $packageName"
        }
        String[] split = packageName.split(QuiltParser.SEP)
        if (split.size()<QuiltParser.MIN_SIZE || split[1].size()<QuiltParser.MIN_SIZE) {
            split += DefaultPackage[1] as String
            log.warn "QuiltID.fetch: setting missing suffix to $split[1]"
        }
        String key = "${bucket}/${split[0]}/${split[1]}"
        if (!ids.containsKey(key)) {
            ids[key] = new QuiltID(bucket, split[0], split[1])
        }
        return ids[key]
    }

    QuiltID(String bucket, String pkgPrefix, String pkgSuffix) {
        this.bucket = bucket
        this.pkgPrefix = pkgPrefix
        this.pkgSuffix = pkgSuffix
    }

    String toString() {
        "${bucket}.${pkgPrefix}.${pkgSuffix}"
    }
}
