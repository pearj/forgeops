import { equals } from "@agiledigital/idm-ts-types/lib/query-filter"
import idm, { ManagedUser } from "lib/idm"
import _ from "lib/lodash"
import { getLogger } from "./common"

const logger = getLogger("pearj.script.user")

/**
 * The algorithm used to store the password history.
 *
 * We are using SHA-512 because although PBKDF2 would be nice, it is far too slow for password history of length 24.
 * As PKBDF2 takes about 150ms whereas SHA-512 is about 0-1ms. 150 * 24 = 3600 (3.6 seconds) which isn't workable.
 */
export const PASSWORD_HISTORY_ALGORITHM = "SHA-512"

export enum ActivationStatus {
  /**
   * User is completely active
   */
  ACTIVE = "active",
  /**
   * User is inactive/disabled
   */
  INACTIVE = "inactive",
  /**
   * User account is ready for a user to go through the activation process
   */
  PENDING = "pending",
  /**
   * User account has all required information and all accounts are in the process of activation
   */
  ACTIVATING = "activating",
  /**
   * The user has started the activation process. This means password sync is available for all accounts.
   */
  STARTED_ACTIVATION = "startedactivation",
}

export function generatePassword(length: number): string {
  const characterSets = ["abcdefghijkmnpqrstuvwxyz", "ABCDEFGHJKLMNPQRSTUVWXYZ", "23456789", "!$%&^#"]
  const allCharacterSets = characterSets.join("")
  const minCountPerSet = [1, 1, 1, 1]
  const minLength = minCountPerSet.reduce((acc, cur) => acc + cur, 0)

  if (minLength > length) {
    throw new Error(`The length of the password must be at least ${minLength}`)
  }

  const password: string[] = new Array(length)
  let index = 0

  for (let i = 0; i < characterSets.length; i++) {
    for (let j = 0; j < minCountPerSet[i]; j++) {
      const generateIndex = Math.floor(Math.random() * characterSets[i].length)
      password[index++] = characterSets[i][generateIndex]
    }
  }

  for (let i = index; i < length; i++) {
    const generateIndex = Math.floor(Math.random() * allCharacterSets.length)
    password[index++] = allCharacterSets[generateIndex]
  }

  // Fisher-Yates shuffle
  for (let i = password.length - 1; i > 0; i--) {
    // random index from 0 to i
    const j = Math.floor(Math.random() * (i + 1))

    // swap elements password[i] and password[j]
    const t = password[i]
    password[i] = password[j]
    password[j] = t
  }

  return password.join("")
}

export function findUserByUserName(userName: string): ManagedUser
export function findUserByUserName(userName: string, returnUndefined: boolean): ManagedUser | undefined
export function findUserByUserName(userName: string, returnUndefined: boolean, additionalUnCheckedFields: string[]): ManagedUser | undefined
export function findUserByUserName(userName: string, returnUndefined = false): ManagedUser | undefined {
  const userRes = idm.managed.user.query({ filter: equals("userName", userName) }, { unCheckedFields: ["*", "*_ref"] })
  const user = userRes.result?.[0]
  if (!user && !returnUndefined) {
    throw new Error(`Couldn't find user [${userName}]`)
  }
  return user
}

/**
 * Generate a random integer between the min and max values
 *
 * @param min The minimum value for the random value
 * @param max The maximuim value for the random value
 * @returns A random integer between min and max
 */
const random = (min: number, max: number): number => Math.floor(Math.random() * (max - min)) + min
