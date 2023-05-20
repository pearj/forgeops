package pearj.scriptedrest

import org.forgerock.http.protocol.Form
import org.forgerock.http.protocol.Request
import org.forgerock.http.protocol.Response
import org.forgerock.openam.audit.context.AuditRequestContext
import org.forgerock.openam.auth.node.api.NodeProcessException
import org.forgerock.openam.scripting.api.http.GroovyHttpClient
import org.forgerock.openam.scripting.api.secrets.ScriptedSecrets
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdmScriptedRest {

    private final ScriptedSecrets secrets
    private final GroovyHttpClient httpClient
    private final Logger logger = LoggerFactory.getLogger(IdmScriptedRest.class)

    IdmScriptedRest(ScriptedSecrets secrets, GroovyHttpClient httpClient) {
        this.secrets = secrets
        this.httpClient = httpClient
    }

    private String fetchAccessToken() {
        def clientId = "idm-provisioning"
        def clientSecret = secrets.getGenericSecret("scripted.node.idm.provisioning.client.secret").asUtf8

        def request = new Request()

        request.method = "POST"
        request.uri = "http://am/am/oauth2/realms/root/access_token"

        def body = [
                grant_type   : ["client_credentials"],
                client_id    : [clientId],
                client_secret: [clientSecret],
                scope        : ["fr:idm:*"]
        ]
        def form = new Form()
        form.addAll(body)
        request.entity = form

        def response = httpClient.send(request).getOrThrow()

        return response.entity.json?.access_token as String
    }

    private void handleHttpError(Response response, String requestUri) {
        logger.debug("HTTP Request was not successful\nuri: {}\nstatus: {}\nbody: {}", requestUri, response.status.toString(), response?.entity?.json)
        throw new NodeProcessException("IDM HTTP Request was not successful for resource: [${requestUri}] status: [${response.status.toString()}]".toString());
    }

    private Object execRequest(String method, String resource, Map query, List<String> fields, Map<String, String> headers = [:], Optional<Object> body = Optional.empty(), boolean returnRaw = false) {
        logger.debug("Executing request ${resource} with method ${method}")

        if (fields.size() > 0) {
            query._fields = fields.join(',')
        }

        def request = new Request()

        request.method = method

        request.uri = "http://idm/openidm/" + resource

        if (query && query.size() > 0) {
            def queryForm = new Form()
            // Wrap the value in an array as expected by the Form class
            query.collect { it.value = [it.value] }

            queryForm.addAll(query)

            queryForm.toRequestQuery(request)
        }

        request.headers.add("Authorization", "Bearer " + fetchAccessToken())

        // Add transaction information
        def auditContext = AuditRequestContext.getAuditRequestContext()
        request.headers.add("X-ForgeRock-TransactionId", auditContext.createSubTransactionIdValue())
        request.headers.add("X-ForgeRock-TrackingIds", auditContext.getTrackingIds())

        if (headers.size() > 0) {
            request.headers.addAll(headers)
        }

        body.map(_body -> request.setEntity(_body))

        def response = httpClient.send(request).getOrThrow()

        if (returnRaw) {
            return response
        } else {
            if (response.status.successful) {
                return response.entity.json
            } else {
                handleHttpError(response, resource)
            }
        }
    }

    Optional<Map> read(String resource, Map params = [:], List<String> fields = []) {
        def response = execRequest("GET", resource, params, fields, [:], Optional.empty(), true) as Response
        if (response.status.successful) {
            return Optional.of(response.entity.json as Map)
        } else if (response.status.code == 404) {
            return Optional.empty()
        } else {
            handleHttpError(response, resource)
        }
    }

    Map query(String resource, Map params = [:], List<String> fields = []) {
        return execRequest("GET", resource, params, fields) as Map
    }

    Map patch(String resource, Optional<String> rev, List<Map> value, Map params = [:], List<String> fields = []) {
        Map<String, String> headers = [:]
        rev.map(_rev -> headers.put("If-Match", _rev))
        return execRequest("PATCH", resource, params, fields, headers, Optional.of(value)) as Map
    }

    Map create(String resource, Optional<String> newResourceId, List<Map> content, Map params = [:], List<String> fields = []) {
        Map<String, String> headers = [:]
        String method
        String resourceRequest
        if (newResourceId.present) {
            method = "PUT"
            resourceRequest = "${resource}/${newResourceId}"
            headers.put("If-None-Match", "*")
        } else {
            method = "POST"
            resourceRequest = resource
        }

        return execRequest(method, resourceRequest, params, fields, headers, Optional.of(content)) as Map
    }

    Map update(String resource, Optional<String> rev, List<Map> value, Map params = [:], List<String> fields = []) {
        Map<String, String> headers = [:]
        // Default to "*" if a revision isn't specified, which means the revision doesn't matter
        // see: https://backstage.forgerock.com/docs/idm/7.1/rest-api-reference/sec-about-crest.html#about-crest-update
        headers.put("If-Match", rev.orElse("*"))
        return execRequest("PUT", resource, params, fields, headers, Optional.of(value)) as Map

    }

    Map delete(String resource, Optional<String> rev, Map params = [:], List<String> fields = []) {
        Map<String, String> headers = [:]
        rev.map(_rev -> headers.put("If-Match", _rev))
        return execRequest("DELETE", resource, params, fields, headers) as Map
    }

    Map action(String resource, String actionName, Map content = [:], Map params = [:], List<String> fields = []) {
        Map<String, String> headers = [:]
        params.put("_action", actionName)
        def method = "POST"

        return execRequest(method, resource, params, fields, headers, Optional.of(content)) as Map
    }
}