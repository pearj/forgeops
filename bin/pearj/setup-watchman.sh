#!/bin/bash

SCRIPT_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
CDK_BASE=${SCRIPT_DIR}/../../docker/am/config-profiles/cdk
SCRIPTING_DIR=config/services/realm/root/scriptingservice/1.0/organizationconfig/default/scriptconfigurations
AM_ADMIN_PASSWORD=$(kubectl get secret am-env-secrets -o go-template='{{index .data "AM_PASSWORDS_AMADMIN_CLEAR" | base64decode}}') 
AM_URL=$(kubectl get configmap platform-config -o go-template='{{index .data "AM_URL"}}') 

watchman watch-del-all

watchman watch ${CDK_BASE}/${SCRIPTING_DIR}

# watchman -- trigger ${CDK_BASE}/${SCRIPTING_DIR} deployamscript '*.groovy' -- ${SCRIPT_DIR}/reload-scripts.sh
watchman -- trigger ${CDK_BASE}/${SCRIPTING_DIR} deployamscript '*.groovy' -- ${SCRIPT_DIR}/update-scripts.py --base-dir ${CDK_BASE}/${SCRIPTING_DIR} --username amadmin --password ${AM_ADMIN_PASSWORD} --base-am-url ${AM_URL}

exec tail -F $(watchman get-log | jq -r .log)