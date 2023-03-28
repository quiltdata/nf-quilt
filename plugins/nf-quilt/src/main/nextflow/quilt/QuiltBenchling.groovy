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
package nextflow.quilt

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator

/**
 * Push Quilt package data to Benchling notebook
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */
@Slf4j
@CompileStatic
class QuiltBenchling {

    private final static String MOCK_URI = 'https://mock-benchling.free.beeceptor.com'
    private final RESTClient client

    QuiltBenchling(boolean isMock=false) {
        log.debug("Creating QuiltBenchling: $isMock")
        client = isMock ? mockClient() : realClient()
    }

    RESTClient mockClient() {
        return new RESTClient(QuiltBenchling.MOCK_URI)
    }

    RESTClient realClient() {
        String tenant = System.getenv('BENCHLING_TENANT')
        String api_key = System.getenv('BENCHLING_TENANT')
        RESTClient client = new RESTClient(tenant)
        client.headers['Authorization'] = "Basic ${api_key}"
        return client
    }

    HttpResponseDecorator get() {
        def authenticationTokenRequestParams = ['key':'AAABBBCCC123', 'user':'myauthemail@bla.com']
        Object resp = client.get(path : '/authenticate', query : authenticationTokenRequestParams)
        return (HttpResponseDecorator) resp
    }

}
