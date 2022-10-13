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

package nextflow.quilt3

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.exception.AbortOperationException
/**
 * Model Quilt config options
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@CompileStatic
class QuiltOpts {

    static Map<String,String> env = System.getenv()

    private String registry
    private String profile

    String getProjectId() { projectId }

    @Memoized
    static QuiltOpts fromSession(Session session) {
        try {
            return fromSession0(session.config)
        }
        catch (Exception e) {
            if(session) session.abort()
            throw e
        }
    }

    protected static QuiltOpts fromSession0(Map config) {
        final result = new QuiltOpts()
        result.registry = config.navigate("Quilt3.registry") as String
        result.profile = config.navigate("Quilt3.profile") as String
        return result
    }

    static QuiltOpts create(Session session) {
        // Typically the credentials picked up are the "AWS Credentials"
        // as described at:
        //   https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html
        //
        // If not using the default, the profile needs to be set in the nextflow config file.

        final config = fromSession(session)
        return config
    }

}
