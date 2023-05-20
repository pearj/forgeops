import pearj.scriptedrest.IdmScriptedRest
import org.forgerock.json.JsonValue
import org.forgerock.openam.auth.node.api.Action
import org.forgerock.openam.auth.node.api.NodeProcessException
import org.forgerock.openam.auth.node.api.NodeState
import org.forgerock.openam.auth.node.api.SharedStateConstants
import org.forgerock.openam.scripting.api.secrets.ScriptedSecrets
import org.forgerock.openam.scripting.idrepo.ScriptIdentityRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.forgerock.openam.scripting.api.http.GroovyHttpClient

import javax.security.auth.callback.Callback

import static java.util.stream.Collectors.toList
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

class ScriptedClassActivationSaveProgress {
    static final Logger logger = LoggerFactory.getLogger("pearj.activation.ActivationSaveProgress")

    private final IdmScriptedRest openidm

    private final NodeState nodeState
    private final ScriptedSecrets secrets
    private final GroovyHttpClient httpClient
    private final JsonValue username
    private String outcome

    ScriptedClassActivationSaveProgress(NodeState nodeState, ScriptedSecrets secrets, GroovyHttpClient httpClient) {
        this.nodeState = nodeState
        this.secrets = secrets
        this.httpClient = httpClient
        this.openidm = new IdmScriptedRest(secrets, httpClient)
        this.username = nodeState.get(SharedStateConstants.USERNAME)
    }

    private boolean fieldExists(String fieldName) {
        if (nodeState.isDefined(fieldName)) {
            return true
        } else {
            // Some newer IDM nodes store their data in "objectAttributes"
            // It is possible that if objectAttributes exists in multiple states then only the first one will be searched
            if (nodeState.isDefined(SharedStateConstants.OBJECT_ATTRIBUTES)) {
                return nodeState.get(SharedStateConstants.OBJECT_ATTRIBUTES).isDefined(fieldName)
            }
        }

        return false
    }

    private JsonValue getValueFromNodeState(String field) {
        def value = nodeState.get(field)
        if (value == null) {
            // Newer IDM based Nodes store their state inside "Object Attributes"
            def objAttr = nodeState.get(SharedStateConstants.OBJECT_ATTRIBUTES)
            value = objAttr.get(field)
        }

        return value
    }

    private void saveProgress(String action, List<String> fieldsToSend) {
        def payload = json(object(field("userName", this.username)))

        def missingFields = fieldsToSend.stream()
                .filter(field -> !fieldExists(field))
                .collect(toList())

        if (!missingFields.isEmpty()) {
            throw new NodeProcessException("Some fields are missing for action " + action + " missing fields: " + json(missingFields).toString())
        }

        fieldsToSend.forEach(field -> payload.add(field, getValueFromNodeState(field)))

        openidm.action("endpoint/accountActivation/saveProgress", action, payload.asMap())

        this.outcome = "Saved"
    }

    Tuple2<Action, String> process() {

        def activationState = nodeState.get("activationState")
        if (activationState == null) {
            throw new NodeProcessException("activationState is missing in the shared state")
        }

        switch (activationState.asString()) {
            case "Start Activation":
                saveProgress("startActivation", [] as List<String>)
                break
            case "Change Password":
                saveProgress("changePassword", [SharedStateConstants.PASSWORD])
                break
            case "Enrol KBA":
                saveProgress("enrolKba", ["kbaInfo"])
                break
            case "Intermediate Activation":
                saveProgress("intermediateActivation", [] as List<String>)
                break
            case "Final Activation":
                saveProgress("finalActivate", [] as List<String>)
                this.outcome = "Complete"
                break
            default:
                throw new NodeProcessException("Unexpected Activation State [${activationState}]".toString())
        }

        return new Tuple2(null, this.outcome)
    }
}

def result = new ScriptedClassActivationSaveProgress(nodeState, secrets, httpClient).process()
action = result.getV1()
outcome = result.getV2()
