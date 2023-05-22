#!/bin/bash

SCRIPT_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
CDK_BASE=${SCRIPT_DIR}/../../docker/am/config-profiles/cdk
SCRIPTING_DIR=config/services/realm/root/scriptingservice/1.0/organizationconfig/default/scriptconfigurations

watchman watch-del-all

watchman watch ${CDK_BASE}/${SCRIPTING_DIR}

watchman -- trigger ${CDK_BASE}/${SCRIPTING_DIR} deployamscript '*.groovy' -- ${SCRIPT_DIR}/reload-scripts.sh

exec tail -F $(watchman get-log | jq -r .log)