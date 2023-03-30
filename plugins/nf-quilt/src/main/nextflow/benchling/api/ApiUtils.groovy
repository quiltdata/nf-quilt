/* groovylint-disable UnusedMethodParameter */
package benchling.api

import groovy.util.logging.Slf4j
import groovy.transform.CompileStatic
import groovyx.net.http.RESTClient
import groovyx.net.http.HttpResponseDecorator

@Slf4j
@CompileStatic
class ApiUtils {

    RESTClient getClient(String basePath) {
        String tenant = basePath || System.getenv('BENCHLING_TENANT')
        String api_key = System.getenv('BENCHLING_API_KEY')
        if (api_key == null || !tenant.startsWith('https://')) {
            log.error("missing_envars.BENCHLING_TENANT[$tenant].BENCHLING_API_KEY[$api_key]")
            return null
        }

        RESTClient client = new RESTClient(tenant)
        client.headers['Authorization'] = "Basic ${api_key}"
        return client
    }

    HttpResponseDecorator http_call(Map<String,?> args) {
        RESTClient http = getClient((String) args.uri)
        String method = String.valueOf(args.method).toLowerCase()
        switch (method) {
        case 'path':
                return (HttpResponseDecorator) http.patch(args)
        case 'post':
                return (HttpResponseDecorator) http.post(args)
        case 'put':
                return (HttpResponseDecorator) http.put(args)
        default:
            return (HttpResponseDecorator) http.get(args)
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

        println "url=$basePath uriPath=$resourcePath query=$queryParams"
        HttpResponseDecorator resp = http_call(
            method : method,
            uri : basePath,
            path : resourcePath,
            body : bodyParams,
            query : queryParams,
            contentType : contentType,
        )

        if (resp.isSuccess() && type != null) {
            String json = resp.getData()
            onSuccess(parse(json, container, type))
        } else {
            onFailure(resp.getStatus(), resp.getStatusLine())
        }
    }

    private Object parse(object, container, Class clazz) {
        if (container == 'array') {
            return object.collect { dict -> parse(dict, '', clazz) }
        }
        return clazz.newInstance(object)
    }

}
