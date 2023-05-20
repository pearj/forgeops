import org.forgerock.openam.auth.node.api.Action
import org.forgerock.openam.auth.node.api.NodeProcessException
import org.forgerock.openam.auth.node.api.NodeState
import org.forgerock.openam.authentication.callbacks.PollingWaitCallback
import org.forgerock.openam.scripting.api.secrets.ScriptedSecrets
import org.forgerock.openam.scripting.idrepo.ScriptIdentityRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.forgerock.openam.scripting.api.http.GroovyHttpClient

import javax.security.auth.callback.Callback
import java.time.Clock
import java.time.Instant

import static org.forgerock.json.JsonValue.json

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

class ScriptedClassWaitingMessage {
    static final Logger logger = LoggerFactory.getLogger("pearj.activation.WaitingMessage")

    static final String WAITING_MESSAGE_DELAY = "waitingMessageDelay"
    static final String WAITING_MESSAGE = "waitingMessage"
    static final String END_TIME_KEY = "waitingMessage.end_time"
    static final String CALLBACK_DONE = "true"

    private final NodeState nodeState
    private final Map sharedState

    ScriptedClassWaitingMessage(NodeState nodeState, Map sharedState) {
        this.nodeState = nodeState
        this.sharedState = sharedState
    }

    void clearState() {
        this.sharedState.remove(END_TIME_KEY)
    }

    private Instant getEndTime() {
        return Instant.ofEpochMilli(nodeState.get(END_TIME_KEY).asLong());
    }

    private static Instant now() {
        return Clock.systemDefaultZone().instant();
    }

    boolean waitTimeCompleted() {
        return now().isAfter(getEndTime())
    }

    boolean waitingMessageDefined() {
        return nodeState.isDefined(WAITING_MESSAGE) && nodeState.isDefined(WAITING_MESSAGE_DELAY)
    }

    Callback prepareCallback() {
        def delaySeconds = nodeState.get(WAITING_MESSAGE_DELAY).asInteger()
        def message = nodeState.get(WAITING_MESSAGE).asString()

        nodeState.putShared(END_TIME_KEY, now().plusSeconds(delaySeconds).toEpochMilli())

        logger.debug("Waiting for {} seconds with message [{}]", delaySeconds, message)
        return new PollingWaitCallback.PollingWaitCallbackBuilder()
                .withWaitTime(String.valueOf(delaySeconds * 1000))
                .withMessage(message).build()
    }

    Tuple2<Action, String> process() {
        if (nodeState.isDefined(END_TIME_KEY) && waitTimeCompleted()) {
            logger.debug("Waiting time has finished, finishing callback")
            clearState()
            return new Tuple2(Action.goTo(CALLBACK_DONE).replaceSharedState(json(sharedState)).build(), CALLBACK_DONE)
        } else if (waitingMessageDefined()) {
            return new Tuple2(Action.send(prepareCallback()).build(), null)
        }
//        else {
//            return new Tuple2(null, CALLBACK_DONE)
//        }

        throw new NodeProcessException("Waiting message and delay must be defined or callback too early")
    }
}

def result = new ScriptedClassWaitingMessage(nodeState, sharedState).process()
action = result.getV1()
outcome = result.getV2()
