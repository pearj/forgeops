//file:noinspection GrDeprecatedAPIUsage
import pearj.scriptedrest.IdmScriptedRest
import org.forgerock.json.JsonValue
import org.forgerock.openam.auth.node.api.Action
import org.forgerock.openam.auth.node.api.NodeProcessException
import org.forgerock.openam.auth.node.api.NodeState
import org.forgerock.openam.auth.node.api.SharedStateConstants
import org.forgerock.openam.authentication.callbacks.ValidatedPasswordCallback
import org.forgerock.openam.scripting.api.secrets.ScriptedSecrets
import org.forgerock.openam.scripting.idrepo.ScriptIdentityRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.forgerock.openam.scripting.api.http.GroovyHttpClient

import javax.security.auth.callback.Callback
import javax.security.auth.callback.ConfirmationCallback
import javax.security.auth.callback.TextOutputCallback

import static org.forgerock.json.JsonValue.json
import static org.forgerock.json.JsonValue.object

def sharedState = sharedState as Map
def transientState = transientState as Map
//noinspection GroovyUnusedAssignment
def nodeState = nodeState as NodeState
def callbacks = callbacks as List<? extends Callback>
//noinspection GroovyUnusedAssignment
def idRepository = idRepository as ScriptIdentityRepository
def secrets = secrets as ScriptedSecrets
// To read the audit data it looks like it's a JsonValue, but to write it back it expects a String or a Map
//def auditEntryDetail = auditEntryDetail as JsonValue
//noinspection GroovyUnusedAssignment
def requestParameters = requestParameters as Map<String, List<String>>
//noinspection GroovyUnusedAssignment
def requestHeaders = requestHeaders as Map<String, List<String>>
//def logger = logger as Debug

//noinspection GroovyUnusedAssignment
def realm = realm as String
def httpClient = httpClient as GroovyHttpClient

// This would normally be an enum, but for some reason enum's don't compile in AM Scripted Nodes
class ScriptedClassPasswordType {
    static ScriptedClassPasswordType NEW = new ScriptedClassPasswordType("newPassword", "New Password", "Please choose a new password")
    static ScriptedClassPasswordType CONFIRM = new ScriptedClassPasswordType("confirmPassword", "Confirm Password", "Please type your new password again")

    final String key
    final String prompt
    final String description

    private ScriptedClassPasswordType(String key, String prompt, String description) {
        this.key = key
        this.prompt = prompt
        this.description = description
    }
}

class ScriptedClassChangePassword {
    static final Logger logger = LoggerFactory.getLogger("pearj.activation.ChangePassword")

    static final String CALLBACK_DONE = "true"

    static final String PASSWORD_RESET_TOKEN_VERIFIED = "passwordResetTokenVerified"

    private final IdmScriptedRest openidm

    private final Map<String, Object> sharedState
    private final Map transientState
    private final ScriptedSecrets secrets
    private final GroovyHttpClient httpClient
    private final List<? extends Callback> callbacks
    private final Optional<ValidatedPasswordCallback> returnedPasswordCallback

    ScriptedClassChangePassword(Map<String, Object> sharedState, Map transientState, ScriptedSecrets secrets, GroovyHttpClient httpClient, List<? extends Callback> callbacks) {
        this.sharedState = sharedState
        this.transientState = transientState
        this.secrets = secrets
        this.httpClient = httpClient
        this.openidm = new IdmScriptedRest(secrets, httpClient)
        this.callbacks = callbacks
        this.returnedPasswordCallback = this.getCallback(ValidatedPasswordCallback.class)
    }

    private JsonValue getPasswordPolicy(ScriptedClassPasswordType passwordType) {
        logger.debug("Retrieving policy validation requirement for password")
        def userPolicy = openidm.read("policy/managed/user")
        if (userPolicy.empty) {
            throw new IllegalStateException("Can't find user policy")
        }

        return json(userPolicy.get()).get("properties").stream()
                .filter(requirement -> requirement.get("name").asString().equals(passwordType.key))
                .findFirst()
                .orElse(json(object()))
    }

    private JsonValue validateInput(Map<String, Object> inputFields, Set<String> properties) {
        def propertyMap = [:]
        properties.forEach(property -> propertyMap.put(property, inputFields.get(property)))
        def payload = [object: inputFields, properties: propertyMap]

        return json(this.openidm.action("policy/managed/user", "validateProperty", payload))
    }

    /**
     * Validates password input using IDM's policies.
     *
     * Example failed policy check:
     * <pre>
     *  {
     *      "result": false,
     *      "failedPolicyRequirements": [
     *          {
     *              "policyRequirements": [
     *                  {
     *                      "params": {
     *                          "minLength": 8
     *                      },
     *                      "policyRequirement": "MIN_LENGTH"
     *                  }
     *              ],
     *              "property": "password"
     *          }
     *      ]
     *  }
     * </pre>
     */
    private boolean checkPassword(ScriptedClassPasswordType passwordType, String callbackPassword, ValidatedPasswordCallback newPasswordCallback) throws NodeProcessException {
        logger.debug("Validating new password")

        def userName = this.sharedState.get(SharedStateConstants.USERNAME)
        if (userName == null) {
            throw new NodeProcessException("Username is not defined")
        }

        def userObj = ["userName": userName, (SharedStateConstants.PASSWORD): callbackPassword]

        def passwordFields = [ (SharedStateConstants.PASSWORD) ]

        if (passwordType == ScriptedClassPasswordType.CONFIRM) {
            // When confirming the password, add the new password using the confirm key so we can check we match the new password
            userObj.put(ScriptedClassPasswordType.CONFIRM.key, this.sharedState.get(ScriptedClassPasswordType.NEW.key))
            passwordFields.add(ScriptedClassPasswordType.CONFIRM.key)
        }

        def result = validateInput(userObj, passwordFields.toSet())
        if (!result.isDefined("result")) {
            throw new NodeProcessException("Communication failure")
        }
        if (!result.get("result").asBoolean()) {
            result.get("failedPolicyRequirements").stream()
                    .filter(failure -> passwordFields.contains(failure.get("property").asString()))
                    .forEach(failure -> failure.get("policyRequirements")
                            .forEach(requirement -> {
                                logger.trace("Password failed policy: {}", requirement.toString())
                                newPasswordCallback.addFailedPolicy(requirement.toString())
                            }))
            return newPasswordCallback.failedPolicies().isEmpty()
        }
        return true
    }

    /**
     * Get the first callback of a particular type from the callbacks in the context.
     *
     * @param callbackType The type of callback.
     * @param <T> The generic type of the callback.
     * @return An optional of the callback or empty if no callback of that type existed.
     */
    private <T extends Callback> Optional<T> getCallback(Class<T> callbackType) {
        return callbacks.stream()
                .filter(c -> callbackType.isAssignableFrom(c.getClass()))
                .map(callbackType::cast)
                .findFirst()
    }

    private static List<? extends Callback> initialiseCallbacks(ScriptedClassPasswordType passwordType, ValidatedPasswordCallback passwordCallback) {
        def callbacks = []

        callbacks.add(new TextOutputCallback(TextOutputCallback.INFORMATION, passwordType.description))
        callbacks.add(passwordCallback)

        // Let the user go back if they mistyped their password the first time.
        if (passwordType == ScriptedClassPasswordType.CONFIRM) {
            callbacks.add(new ConfirmationCallback(ConfirmationCallback.INFORMATION, (String[])["Next", "Go back and choose a new password"], 1))
        }

        return callbacks
    }

    private ValidatedPasswordCallback createPasswordCallback(ScriptedClassPasswordType passwordType) {
        def passwordPolicy = getPasswordPolicy(passwordType)
        return new ValidatedPasswordCallback(passwordType.prompt, false, passwordPolicy, false)
    }

    private JsonValue hashPassword(String password) {
        return json(this.openidm.action("endpoint/accountActivation/password", "hash", [passwordToHash: password]))
    }

    Tuple2<Action, String> process() {
        // Figure out if they want to go back to new password from the confirm password page
        Optional<Integer> confirmationVariable = this.getCallback(ConfirmationCallback.class)
                .map(i -> i.getSelectedIndex())
        Optional<Boolean> goBack = confirmationVariable.map(i -> i.equals(ConfirmationCallback.NO))
        if (goBack.present && goBack.get()) {
            // Remove the new stored password so that we can request it again
            this.sharedState.remove(ScriptedClassPasswordType.NEW.key)
            def action = Action
                    .send(initialiseCallbacks(ScriptedClassPasswordType.NEW, createPasswordCallback(ScriptedClassPasswordType.NEW)))
                    .replaceSharedState(json(this.sharedState))
                    .build()
            return new Tuple2(action, null)
        }

        def newPasswordShared = this.sharedState.get(ScriptedClassPasswordType.NEW.key)

        def passwordType = newPasswordShared == null ? ScriptedClassPasswordType.NEW : ScriptedClassPasswordType.CONFIRM

        def newPasswordCallback = createPasswordCallback(passwordType)

        if (this.returnedPasswordCallback.isEmpty()) {
            logger.debug("Collecting validated password")
            return new Tuple2(Action.send(initialiseCallbacks(passwordType, newPasswordCallback)).build(), null)
        }

        newPasswordCallback.validateOnly(this.returnedPasswordCallback.get().validateOnly())
        def callbackPassword = this.returnedPasswordCallback.get().password.toString()
        if (!checkPassword(passwordType, callbackPassword, newPasswordCallback)) {
            logger.debug("Re-collecting invalid password")

            return new Tuple2(Action.send(initialiseCallbacks(passwordType, newPasswordCallback)).build(), null)
        } else if (this.returnedPasswordCallback.get().validateOnly()) {
            logger.debug("Validation passed but validateOnly is true; Returning callbacks")
            return new Tuple2(Action.send(initialiseCallbacks(passwordType, this.returnedPasswordCallback.get())).build(), null)
        }

        if (passwordType == ScriptedClassPasswordType.NEW) {
            logger.debug("Storing the new password (hashed) and asking for confirmation password")
            this.sharedState.put(ScriptedClassPasswordType.NEW.key, hashPassword(callbackPassword).get("hashedPassword"))

            return new Tuple2(Action.send(initialiseCallbacks(ScriptedClassPasswordType.CONFIRM, createPasswordCallback(ScriptedClassPasswordType.CONFIRM))).build(), null)
        }

        // If we got this far that means everything is validated and we are done
        this.transientState.put(SharedStateConstants.PASSWORD, callbackPassword)

        // Also store the password in the object attributes so native patch works
        def objAttrs = (this.transientState.get(SharedStateConstants.OBJECT_ATTRIBUTES) ?: [:]) as Map
        objAttrs.put(SharedStateConstants.PASSWORD, callbackPassword)
        this.transientState.put(SharedStateConstants.OBJECT_ATTRIBUTES, objAttrs)

        // Delete the passwords from the shared state
        this.sharedState.remove(ScriptedClassPasswordType.NEW)

        def action = Action
                .goTo(CALLBACK_DONE)
                .replaceSharedState(json(this.sharedState))
                .replaceTransientState(json(this.transientState))
                .build()
        return new Tuple2(action, null)
    }
}

def result = new ScriptedClassChangePassword(sharedState, transientState, secrets, httpClient, callbacks).process()
//noinspection GroovyUnusedAssignment
outcome = result.getV2()
//noinspection GroovyUnusedAssignment
action = result.getV1()
