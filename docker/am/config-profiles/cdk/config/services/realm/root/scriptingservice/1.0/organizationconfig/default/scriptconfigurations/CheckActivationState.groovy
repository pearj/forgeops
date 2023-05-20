import pearj.scriptedrest.IdmScriptedRest
import org.forgerock.openam.auth.node.api.Action
import org.forgerock.openam.auth.node.api.NodeState
import org.forgerock.openam.auth.node.api.SharedStateConstants
import org.forgerock.openam.scripting.api.secrets.ScriptedSecrets
import org.forgerock.openam.scripting.idrepo.ScriptIdentityRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.forgerock.openam.scripting.api.http.GroovyHttpClient

import javax.security.auth.callback.Callback

import static org.forgerock.json.JsonValue.field
import static org.forgerock.json.JsonValue.json
import static org.forgerock.json.JsonValue.object

def sharedState = sharedState as Map
def transientState = transientState as Map
def nodeState = nodeState as NodeState
def callbacks = callbacks as List<? extends Callback>
def idRepository = idRepository as ScriptIdentityRepository
def secrets = secrets as ScriptedSecrets
// To read the audit data it looks like it's a JsonValue, but to write it back it expects a String or a Map
//def auditEntryDetail = auditEntryDetail as JsonValue
def requestParameters = requestParameters as Map<String, List<String>>
def requestHeaders = requestHeaders as Map<String, List<String>>
//def logger = logger as Debug

def realm = realm as String
def httpClient = httpClient as GroovyHttpClient

// An Action can be defined, but it isn't required
// Action action =

class ScriptedClassCheckActivationState {
    static final Logger logger = LoggerFactory.getLogger("pearj.activation.CheckActivationState")

    static final String ACTIVATION_PREFIX = "activation."

    private final IdmScriptedRest openidm

    private final Map<String, Object> sharedState
    private final Map transientState
    private final ScriptedSecrets secrets
    private final GroovyHttpClient httpClient

    ScriptedClassCheckActivationState(Map<String, Object> sharedState, Map transientState, ScriptedSecrets secrets, GroovyHttpClient httpClient) {
        this.sharedState = sharedState
        this.transientState = transientState
        this.secrets = secrets
        this.httpClient = httpClient
        this.openidm = new IdmScriptedRest(secrets, httpClient)
    }

    Tuple2<Action, String> process() {
        def payload = json(object(field("userName", this.sharedState.get(SharedStateConstants.USERNAME))))
        this.sharedState.entrySet().forEach(entry -> {
            if (entry.key.startsWith(ACTIVATION_PREFIX)) {
                payload.add(entry.key, entry.value)
            }
        })
        def activationState = openidm.action("endpoint/accountActivation/activationState", "check", payload.asMap())
        def outcome = activationState.outcome
        logger.debug("Activation State Outcome [{}]", outcome)
        this.sharedState.put("activationState", outcome)
        if (activationState.transientState instanceof Map) {
            if (logger.debugEnabled) {
                logger.debug("Activation State TransientState {}", json(activationState.transientState).toString())
            }
            this.transientState.putAll(activationState.transientState as Map)
        }
        if (activationState.sharedState instanceof Map) {
            if (logger.debugEnabled) {
                logger.debug("Activation State SharedState {}", json(activationState.sharedState).toString())
            }
            this.sharedState.putAll(activationState.sharedState as Map)
        }

        return new Tuple2(null, outcome)
    }
}

def result = new ScriptedClassCheckActivationState(sharedState, transientState, secrets, httpClient).process()
action = result.getV1()
outcome = result.getV2()
