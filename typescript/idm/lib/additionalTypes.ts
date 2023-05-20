type SyncDetails = {
  reconId: string
  mapping: string
  situation: string
  action: string
  sourceId: string
  linkQualifier: string
  targetId: string
  ambiguousTargetIds: unknown
  oldTargetValue: unknown
  result: string
  targetObjectSet: string
}

type SyncResults = {
  success: boolean
  action: string
  syncDetails: SyncDetails[]
}

type Policy = {
  policyId: string
  policyExec: string
  policyRequirements: string[]
  validateOnlyIfPresent?: boolean
}

type PolicyRequirement = {
  policyRequirement: string
  params?: Record<string, any>
}

type FailedPolicyRequirement = {
  policyRequirements: PolicyRequirement[]
  property: string
}

type PolicyResponse = {
  result: boolean
  failedPolicyRequirements: FailedPolicyRequirement[]
}

type CustomEndpointMethod = "create" | "read" | "update" | "patch" | "query" | "delete" | "action"

interface CustomEndpointAbstractRequest {
  readonly additionalParameters: Record<string, string>
  // readonly method: CustomEndpointMethod
  readonly resourcePath: string
  readonly preferredLocales: unknown
  readonly fields: string[]
}

interface CustomEndpointReadRequest extends CustomEndpointAbstractRequest {
  readonly method: "read"
}

interface CustomEndpointActionRequest extends CustomEndpointAbstractRequest {
  readonly method: "action"
  readonly action: string
  readonly content: Record<string, any>
  readonly additionalParameters: Record<string, string>
}

interface CustomEndpointPatchRequest extends CustomEndpointAbstractRequest {
  readonly method: "patch"
  readonly revision: string
  readonly patchOperations: PatchOperation[]
}

/**
 * Represents a `org.forgerock.util.query.QueryFilter`.
 * The only function of interest is likely `toString()`.
 */
interface JavaQueryFilter {
  toString(): string
}
interface CustomEndpointQueryRequest extends CustomEndpointAbstractRequest {
  readonly method: "query"
  readonly pagedResultsCookie: string
  readonly pagedResultsOffset: string
  readonly pageSize: string
  readonly queryId: string
  readonly queryFilter: JavaQueryFilter
}

type CustomEndpointRequest = CustomEndpointReadRequest | CustomEndpointPatchRequest | CustomEndpointQueryRequest | CustomEndpointActionRequest

type LinkedResource = {
  resourceName: string
  content?: IDMBaseObject & Record<string, any>
  linkQualifier: string
  linkType: string
}

type PolicyRequest = {
  resourcePath: string
}

interface AbstractContext {
  class: string
  name: string
  parent: any
}

interface SecurityAuthorizationContext {
  /**
   * The login id / username of the user
   */
  id: string

  /**
   * A list of roles that the user has
   */
  roles: string[]

  /**
   * The component where the user resides.
   * eg: internal/user or maybe managed/user
   */
  component: string
}

interface SecurityContext extends AbstractContext {
  /**
   * The login id / username of the user
   */
  authenticationId: string
  authorization?: SecurityAuthorizationContext
}
interface HttpContext extends AbstractContext {
  method: string
  path: string
  headers: Record<string, string | string[]>
  parameters?: Record<string, string>
}

type RequestContext = {
  security?: SecurityContext
  http?: HttpContext
}
type SituationCount = {
  count: number
}
/**
 * https://backstage.forgerock.com/docs/idm/7.1/synchronization-guide/sync-situations.html
 */
type SituationResult = {
  SOURCE_IGNORED: SituationCount
  FOUND_ALREADY_LINKED: SituationCount
  UNQUALIFIED: SituationCount
  ABSENT: SituationCount
  TARGET_IGNORED: SituationCount
  MISSING: SituationCount
  ALL_GONE: SituationCount
  AMBIGUOUS: SituationCount
  CONFIRMED: SituationCount
  LINK_ONLY: SituationCount
  SOURCE_MISSING: SituationCount
  FOUND: SituationCount
}

/**
 * https://backstage.forgerock.com/docs/idm/7.1/synchronization-guide/sync-situations.html#target-reconciliation
 */
type TargetSituationResult = SituationResult

/**
 * https://backstage.forgerock.com/docs/idm/7.1/synchronization-guide/sync-situations.html#source-reconciliation
 */
type SourceSituationResult = {
  UNASSIGNED: SituationResult
} & SituationResult

/**
 * This is an undocumented situation that contains the ids that have been changed
 */
type NotValidSituation = {
  NOT_VALID: { ids: string } & SituationCount
}

type ReconciliationResult = {
  name: string
  startTime: string
  endTime: string
  entryListDuration: number
  processed: number
  duration: number
  entries: number
}

type SourceReconciliationResult = ReconciliationResult & SourceSituationResult & NotValidSituation
type TargetReconciliationResult = ReconciliationResult & TargetSituationResult & NotValidSituation

type GlobalReconciliationResult = {
  mappingName: string
  duration: number
  startTime: string
  endTime: string
  reconId: string
}

/**
 * Active Directory UserAccountControl flag names.
 * Taken from: https://codepen.io/jrjenk/pen/XdMyPq
 * See also: https://learn.microsoft.com/en-US/troubleshoot/windows-server/identity/useraccountcontrol-manipulate-account-properties
 */
type UserAccountControl =
  | "SCRIPT"
  | "ACCOUNTDISABLE"
  | "HOMEDIR_REQUIRED"
  | "LOCKOUT"
  | "PASSWD_NOTREQD"
  | "PASSWD_CANT_CHANGE"
  | "ENCRYPTED_TEXT_PWD_ALLOWED"
  | "TEMP_DUPLICATE_ACCOUNT"
  | "NORMAL_ACCOUNT"
  | "INTERDOMAIN_TRUST_ACCOUNT"
  | "WORKSTATION_TRUST_ACCOUNT"
  | "SERVER_TRUST_ACCOUNT"
  | "DONT_EXPIRE_PASSWORD"
  | "MNS_LOGON_ACCOUNT"
  | "SMARTCARD_REQUIRED"
  | "TRUSTED_FOR_DELEGATION"
  | "NOT_DELEGATED"
  | "USE_DES_KEY_ONLY"
  | "DONT_REQ_PREAUTH"
  | "PASSWORD_EXPIRED"
  | "TRUSTED_TO_AUTH_FOR_DELEGATION"
  | "PARTIAL_SECRETS_ACCOUNT"
