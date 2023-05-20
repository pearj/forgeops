/* eslint-disable @typescript-eslint/no-empty-function */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
Object.defineProperty(globalThis, "openidm", {
  value: {
    query: () => {},
    patch: () => {},
    read: () => {},
    isHashed: () => {},
    matches: () => {},
    hash: () => {},
  },
  writable: true,
})

Object.defineProperty(globalThis, "org", {
  value: {
    slf4j: {
      LoggerFactory: {
        getLogger: () => {
          return {
            error() {
              // do nothing
            },
            warn() {
              // do nothing
            },
            info() {
              // do nothing
            },
            debug() {
              // do nothing
            },
            trace() {
              // do nothing
            },
            isErrorEnabled() {
              return true
            },
            isWarnEnabled() {
              return true
            },
            isInfoEnabled() {
              return true
            },
            isDebugEnabled() {
              return true
            },
            isTraceEnabled() {
              return true
            },
          }
        },
      },
    },
  },
  writable: true,
})
