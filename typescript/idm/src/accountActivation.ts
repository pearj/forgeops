import idm, { ManagedUser } from "lib/idm"
import { getLogger } from "./common"
import { ActivationStatus, findUserByUserName } from "./user"

const logger = getLogger("pearj.script.accountActivation")

export const PASSWORD_HISTORY_ALGORITHM = "SHA-512"

function generateActivationDelayMessage(content: Partial<ActivationSharedState>): string | undefined {
  // eslint-disable-next-line sonarjs/no-duplicate-string
  if (content["activation.activationDelay"]) {
    const activationDelay = new Date(content["activation.activationDelay"])
    const currentTime = new Date()
    if (activationDelay > currentTime) {
      const delaySeconds = (activationDelay.getTime() - currentTime.getTime()) / 1000
      const primaryMessage = "Your account is activating, "
      return primaryMessage + (delaySeconds < 10 ? "almost there!" : "please wait...")
    }
  }

  return undefined
}

function setActivationStatus(user: ManagedUser, newAccountStatus: ActivationStatus): Record<string, any> {
  idm.managed.user.patch(user._id, null, [
    {
      operation: "replace",
      field: "activationStatus",
      value: newAccountStatus,
    },
  ])

  return { success: true }
}

type ActivationSharedState = {
  "activation.activationDelay": string
}

type ActivationTransientState = {
  waitingMessage: string
  waitingMessageDelay: number
}

type ActivateStateResponse = {
  outcome: ActivationState
  transientState?: ActivationTransientState
  sharedState?: ActivationSharedState
}

enum ActivationState {
  NOT_PENDING = "Account Not Pending",
  START_ACTIVATION = "Start Activation",
  ENROL_KBA = "Enrol KBA",
  CHANGE_PASSWORD = "Change Password",
  INTERMEDIATE_ACTIVATION = "Intermediate Activation",
  ACTIVATION_PENDING = "Activation Pending",
  FINAL_ACTIVATION = "Final Activation",
}

function checkActivationState(user: ManagedUser, content: Partial<ActivationSharedState>): ActivateStateResponse {
  if (
    ![ActivationStatus.PENDING, ActivationStatus.ACTIVATING, ActivationStatus.STARTED_ACTIVATION].includes(user.activationStatus as ActivationStatus)
  ) {
    return { outcome: ActivationState.NOT_PENDING }
  }

  if ([ActivationStatus.PENDING].includes(user.activationStatus as ActivationStatus)) {
    // We're about to start activation, so let's flick over the status to started activation.
    // This enables password sync to happen in the change password step, where before it might have been disabled for the PENDING_WITH_PUD status
    return { outcome: ActivationState.START_ACTIVATION }
  }

  if (user.changePassword === true) {
    return { outcome: ActivationState.CHANGE_PASSWORD }
  }

  if ((user.kbaInfo?.length ?? 0) === 0) {
    return { outcome: ActivationState.ENROL_KBA }
  }

  if (user.activationStatus === ActivationStatus.STARTED_ACTIVATION) {
    // Set a shared state that is the activation time, so that we can wait 30 seconds if it hasn't elapsed by final activation.
    const activationDelay = new Date()
    activationDelay.setSeconds(activationDelay.getSeconds() + 30)
    return { outcome: ActivationState.INTERMEDIATE_ACTIVATION, sharedState: { "activation.activationDelay": activationDelay.toISOString() } }
  }

  const syncingMessage = generateActivationDelayMessage(content)
  if (syncingMessage !== undefined) {
    return { outcome: ActivationState.ACTIVATION_PENDING, transientState: { waitingMessage: syncingMessage, waitingMessageDelay: 5 } }
  }

  return { outcome: ActivationState.FINAL_ACTIVATION }
}

function enrolKba(user: ManagedUser, content: Record<string, any>): Record<string, any> {
  idm.managed.user.patch(user._id, null, [
    {
      operation: "replace",
      field: "kbaInfo",
      value: content.kbaInfo,
    },
  ])

  return { success: true }
}

function changePassword(user: ManagedUser, content: Record<string, any>): Record<string, any> {
  idm.managed.user.patch(user._id, null, [
    {
      operation: "replace",
      field: "password",
      value: content.password,
    },
  ])

  return { success: true }
}

type PasswordHashState = {
  passwordToHash?: string
}

function hashPassword(content: PasswordHashState): Record<string, any> {
  return { hashedPassword: openidm.hash(content.passwordToHash, PASSWORD_HISTORY_ALGORITHM) }
}

// eslint-disable-next-line sonarjs/cognitive-complexity
export function handleRequest(request: CustomEndpointRequest): Record<string, any> {
  if (request.method === "action") {
    if (request.resourcePath === "password" && request.action === "hash") {
      return hashPassword(request.content)
    }
    const user = findUserByUserName(request.content.userName)
    if (request.resourcePath === "activationState" && request.action === "check") {
      return checkActivationState(user, request.content)
    } else if (request.resourcePath === "saveProgress") {
      switch (request.action) {
        case "startActivation":
          return setActivationStatus(user, ActivationStatus.STARTED_ACTIVATION)
        case "changePassword":
          return changePassword(user, request.content)
        case "enrolKba":
          return enrolKba(user, request.content)
        case "intermediateActivation":
          return setActivationStatus(user, ActivationStatus.ACTIVATING)
        case "finalActivate":
          return setActivationStatus(user, ActivationStatus.ACTIVE)
        default:
          throw `Unsupported action [${request.action}]`
      }
    } else {
      throw `Unsupported resource [${request.resourcePath}]`
    }
  }
  return {}
}
