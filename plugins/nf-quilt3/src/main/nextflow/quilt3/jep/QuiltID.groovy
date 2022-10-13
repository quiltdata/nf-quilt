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

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class QuiltID {
    public static String[] DEFAULT_PACKAGE=["null","default"]

    private static final Map<String,QuiltID> ids = [:]

    static public QuiltID Fetch(String bucket, String pkg_name) {
        if (!bucket) {
            log.error "null == QuiltID.Fetch($bucket, $pkg_name)"
            return null
        }
        if (!pkg_name || pkg_name.size()<QuiltParser.MIN_SIZE) {
            pkg_name = DEFAULT_PACKAGE.join(QuiltParser.SEP)
            log.error "QuiltID.Fetch: setting missing package to $pkg_name"
        }
        String[] split = pkg_name.split(QuiltParser.SEP)
        if (split.size()<QuiltParser.MIN_SIZE || split[1].size()<QuiltParser.MIN_SIZE) {
            split += DEFAULT_PACKAGE[1] as String
            log.error "QuiltID.Fetch: setting missing suffix to $split[1]"
        }
        String key = "${bucket}/${split[0]}/${split[1]}"
        def id = ids.get(key)
        if (id) return id
        ids[key] = new QuiltID(bucket, split[0], split[1])
        ids[key]
    }

    private final String bucket
    private final String pkg_prefix
    private final String pkg_suffix

    QuiltID(String bucket, String pkg_prefix, String pkg_suffix) {
        this.bucket = bucket
        this.pkg_prefix = pkg_prefix
        this.pkg_suffix = pkg_suffix
    }

    String toString() {
        "${pkg_suffix}.${pkg_prefix}.${bucket}"
    }
}
