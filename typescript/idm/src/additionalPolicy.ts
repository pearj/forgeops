import { getLogger } from "./common"

declare const request: PolicyRequest
declare const context: RequestContext

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const logger = getLogger("pearj.script.additionalPolicy")

/**
 * This password is used by the platform-ui as a test password to discover all the password criteria
 */
const TEST_PASSWORD = "aaa"

const isNonEmptyString = (value: string): boolean => typeof value === "string" && value.length > 0

const supplyDefaults = <T extends Record<string, any>>(supplied: T, defaults: Required<T>): Required<T> => ({ ...defaults, ...supplied })

function maximumArrayLengthPolicy(): Policy {
  return {
    policyId: "maximumArrayLength",
    policyExec: "maximumArrayLength",
    policyRequirements: ["MAXIMUM_ARRAY_LENGTH"],
    validateOnlyIfPresent: true,
  }
}

type MaximumArrayLengthParams = {
  maximumItems: string
  typeName: string
}

export function maximumArrayLength(fullObject: unknown, value: Array<unknown> | undefined, params: MaximumArrayLengthParams): PolicyRequirement[] {
  const arrayLength = value?.length ?? 0
  if (arrayLength > parseInt(params.maximumItems)) {
    return [
      {
        policyRequirement: "MAXIMUM_ARRAY_LENGTH",
        params: { arrayLength: arrayLength, ...params },
      },
    ]
  }
  return []
}

function enumValuePolicy(): Policy {
  return {
    policyId: "enumValue",
    policyExec: "enumValue",
    policyRequirements: ["ENUM_VALUE"],
    validateOnlyIfPresent: true,
  }
}

type EnumValueParams = {
  values: string[]
}

export function enumValue(fullObject: unknown, value: string | undefined, params: EnumValueParams): PolicyRequirement[] {
  if ((value?.length ?? 0) > 0 && !params.values.includes(value ?? "")) {
    return [
      {
        policyRequirement: "ENUM_VALUE",
        params: { allowedValues: params.values.join(", ") },
      },
    ]
  }
  return []
}

function addsPasswordComplexityPolicy(): Policy {
  return {
    policyId: "addsPasswordComplexity",
    policyExec: "addsPasswordComplexity",
    policyRequirements: ["AT_LEAST_LOWERCASE_LETTERS", "AT_LEAST_UPPERCASE_LETTERS", "AT_LEAST_DIGITS", "AT_LEAST_SPECIAL"],
    validateOnlyIfPresent: true,
  }
}

export function addsPasswordComplexity(fullObject: any, value: string | undefined): PolicyRequirement[] {
  const characterPolicies = ["AT_LEAST_LOWERCASE_LETTERS", "AT_LEAST_UPPERCASE_LETTERS", "AT_LEAST_DIGITS", "AT_LEAST_SPECIAL"]
  // If we get the test password then we should fail all categories, otherwise categories may be missing depending on the test password
  if (value === TEST_PASSWORD) {
    return characterPolicies.map((p) => {
      return { policyRequirement: p }
    })
  }
  if (value && (value?.length ?? 0) > 0) {
    const characterSets = [/[a-z]/, /[A-Z]/, /[0-9]/, /[~!@#$%^&*()\-_=+[\]{}|;:,.<>/?"'\\`]/]
    const policyFailures: PolicyRequirement[] = []
    for (let i = 0; i < characterSets.length; i++) {
      if (!characterSets[i].test(value)) {
        policyFailures.push({ policyRequirement: characterPolicies[i] })
      }
    }
    // We only need 3 out of 4 categories which translates to more than 1 failure.
    if (policyFailures.length > 1) {
      return policyFailures
    }
  }
  return []
}

export function registerAdditionalPolicies(addPolicy: Function): void {
  addPolicy(maximumArrayLengthPolicy())
  addPolicy(enumValuePolicy())
  addPolicy(addsPasswordComplexityPolicy())
}
