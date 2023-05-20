// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const org: any

export enum InternalRole {
  PLATFORM_PROVISIONING = "internal/role/platform-provisioning",
  OPENIDM_ADMIN = "internal/role/openidm-admin",
}

/**
 * A wrapper around the slf4j logger.
 *
 * The wrapper is required because when calling java functions with var-args rhino is unable to select the correct logging function.
 * This is because slf4j logging methods are being overloaded, and Rhino has trouble choosing between var-args and specific
 *
 * To be able to see the log output you need ensure that "%3$s" is added to the logging format, which is the logging class name.
 *
 * For example in logging.properties change:
 *    java.util.logging.SimpleFormatter.format = %1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS.%1$tL %1$Tp %2$s%n%4$s: %5$s%6$s%n
 * to:
 *    java.util.logging.SimpleFormatter.format = %1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS.%1$tL %1$Tp %3$s %2$s%n%4$s: %5$s%6$s%n
 *
 * @param loggerName name of the logger to create
 */
// eslint-disable-next-line @typescript-eslint/explicit-function-return-type
export function getLogger(loggerName: string) {
  const underlyingLogger = org.slf4j.LoggerFactory.getLogger(loggerName)
  return {
    error(message, ...rest): void {
      underlyingLogger.error(message, rest)
    },
    warn(message, ...rest): void {
      underlyingLogger.warn(message, rest)
    },
    info(message, ...rest): void {
      underlyingLogger.info(message, rest)
    },
    debug(message, ...rest): void {
      underlyingLogger.debug(message, rest)
    },
    trace(message, ...rest): void {
      underlyingLogger.trace(message, rest)
    },
    isErrorEnabled(): boolean {
      return underlyingLogger.isErrorEnabled()
    },
    isWarnEnabled(): boolean {
      return underlyingLogger.isWarnEnabled()
    },
    isInfoEnabled(): boolean {
      return underlyingLogger.isInfoEnabled()
    },
    isDebugEnabled(): boolean {
      return underlyingLogger.isDebugEnabled()
    },
    isTraceEnabled(): boolean {
      return underlyingLogger.isTraceEnabled()
    },
  }
}

export function escapeSingleQuotes(unescapedString: string): string {
  return unescapedString.replace(/'/g, "\\'")
}

export function isResourceLinked(actualMappingName: string, firstId: string, secondId: string): boolean {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const syncConfig: any = openidm.read("config/sync")

  // Create a hashmap of all the mappings keyed by name
  const mappingMap = syncConfig.mappings.reduce((acc, mapping) => {
    acc[mapping.name] = mapping
    return acc
  }, {})

  const actualMapping = mappingMap[actualMappingName]
  let queryFilter
  // If we have a link, then we need to figure out what the link is and the order of the ids
  if (actualMapping?.links) {
    const linkedMapping = mappingMap[actualMapping.links]
    if (linkedMapping.source === actualMapping.source) {
      // Linked mapping has the same source and target
      queryFilter = `linkType eq '${linkedMapping.name}' and firstId eq '${firstId}' and secondId eq '${secondId}'`
    } else {
      // Linked mapping has opposite source and target, so we need to flip the ids
      queryFilter = `linkType eq '${linkedMapping.name}' and firstId eq '${secondId}' and secondId eq '${firstId}'`
    }
  } else {
    // No linked mapping, so just use as is
    queryFilter = `linkType eq '${actualMappingName}' and firstId eq '${firstId}' and secondId eq '${secondId}'`
  }

  const linkResults = openidm.query("repo/links", {
    _queryFilter: queryFilter,
  })
  logger.info("Link query {} results {} ", queryFilter, JSON.stringify(linkResults, null, 2))
  return linkResults.result.length > 0
}

export function readRequiredProperty(propertyName: string): string {
  const propertyValue = identityServer.getProperty(propertyName, null, false)
  if (propertyValue === null) {
    throw new Error(`Unable to find required property ${propertyName}`)
  } else {
    return propertyValue
  }
}

/**
 * Flatten a nested array one level deep.
 *
 * Taken from: https://stackoverflow.com/a/66970585
 * @param arr Nested array to flatten
 * @returns An array flattened one level
 */
export function flatten<T>(arr: T[][]): T[] {
  return ([] as T[]).concat(...arr)
}

export const hasPlatformProvisioningRole = (context: RequestContext): boolean =>
  context.security?.authorization?.roles.includes(InternalRole.PLATFORM_PROVISIONING) ?? false
