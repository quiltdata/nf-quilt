/* groovylint-disable UnusedMethodParameter */
package benchling.api

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import groovy.transform.CompileStatic
import wslite.rest.RESTClient
import wslite.rest.Response

@Slf4j
@CompileStatic
class ApiUtils {

    private static String defaultTenant  = 'https://benchling.com/api/v2'

    static void setDefaultTenant(String newDefaultTenant) {
        defaultTenant = newDefaultTenant
    }

    static Map<String, String> getHeaders(String contentType = null) {
        return [
            'Authorization' : "Basic ${System.getenv('BENCHLING_API_KEY')}".toString(),
            'Content-Type' : contentType ?: 'application/json'
        ]
    }
    RESTClient getClient(String basePath = null) {
        String tenant = basePath ?: System.getenv('BENCHLING_TENANT') ?: defaultTenant
        String api_key = System.getenv('BENCHLING_API_KEY')
        if (api_key == null || !tenant.startsWith('https://')) {
            log.warn("missing_envars.BENCHLING_TENANT[$tenant].BENCHLING_API_KEY[$api_key]")
        }

        RESTClient client = new RESTClient(tenant)
        return client
    }

    Response http_call(Map<String, Object> args, String method = 'get') {
        RESTClient http = getClient((String) args.uri)
        Map<String, Object> opts = [
            headers: getHeaders((String) args.contentType),
            query: args.query,
            path: args.path,
            //body: args.body,
        ]
        switch (method) {
        case 'patch':
                return http.patch(opts)
        case 'post':
                return http.post(opts)
        case 'put':
                return http.put(opts)
        default:
            return http.get(opts)
        }
    }

    /* groovylint-disable-next-line ParameterCount */
    Object invokeApi(
        Closure onSuccess,
        Closure onFailure,
        String basePath,
        String versionPath,
        String resourcePath,
        Map queryParams,
        Map headerParams,
        Object bodyParams,
        Object contentType,
        String method,
        String container,
        Class type
        )  {

        //println "url=$basePath uriPath=$resourcePath query=$queryParams"
        Response resp = http_call(
            method,
            uri : basePath,
            path : resourcePath,
            body : bodyParams,
            query : queryParams,
            contentType : contentType,
        )

        log.debug("invokeApi.resp[$method] $resp")
        String json_string = resp.getContentAsString()
        log.debug("invokeApi.json_string $json_string")
        if (resp.statusCode == 200 && type != null) {
            def jsonSlurper = new JsonSlurper()
            def json = jsonSlurper.parseText(json_string)
            onSuccess(parse(json, container, type))
        } else {
            onFailure(resp.statusCode, resp.getContentAsString())
        }
        }

    private Object parse(object, container, Class clazz) {
        if (container == 'array') {
            return object.collect { dict -> parse(dict, '', clazz) }
        }
        return clazz.newInstance(object)
    }

}
