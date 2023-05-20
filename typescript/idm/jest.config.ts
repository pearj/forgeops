// eslint-disable-next-line jest/no-jest-import
import type { Config } from "jest"

const config: Config = {
  preset: "ts-jest",
  testEnvironment: "node",
  transformIgnorePatterns: ["node_modules/(?!(@agiledigital/idm-ts-types)/)"],
  moduleNameMapper: {
    "lib/lodash": "lodash",
    "^lib/(.*)": "<rootDir>/lib/$1",
  },
  // Setup files seems to add a big performance hit unless you run jest single threaded with --runInBand
  setupFiles: ["./testSetup.ts"],
}

export default config
