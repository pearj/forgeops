#!/bin/bash

set -euox pipefail

SCRIPT_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
CDK_BASE=${SCRIPT_DIR}/../../docker/am/config-profiles/cdk
SCRIPTING_DIR=config/services/realm/root/scriptingservice/1.0/organizationconfig/default/scriptconfigurations
COOKIE_FILE=${SCRIPT_DIR}/cookies.txt
AM_ADMIN_PASSWORD=$(kubectl get secret am-env-secrets -o go-template='{{index .data "AM_PASSWORDS_AMADMIN_CLEAR" | base64decode}}') 
AM_URL=$(kubectl get configmap platform-config -o go-template='{{index .data "AM_URL"}}') 

CHANGED_FILE=${1:-}
if [[ -z $CHANGED_FILE ]]; then
  echo "Nothing specified syncing directory"
  kubectl cp ${CDK_BASE}/${SCRIPTING_DIR}/ $(kubectl get pod -l app=am -o jsonpath='{.items[0].metadata.name}'):openam/${SCRIPTING_DIR}/../

else
  echo "Syncing file $CHANGED_FILE"
fi

kubectl cp ${CDK_BASE}/${SCRIPTING_DIR}/${CHANGED_FILE} $(kubectl get pod -l app=am -o jsonpath='{.items[0].metadata.name}'):openam/${SCRIPTING_DIR}/${CHANGED_FILE}
# openam/config/services/realm/root/scriptingservice/1.0/organizationconfig/default/scriptconfigurations/

rm $COOKIE_FILE

curl --fail --insecure --location --request POST "${AM_URL}/json/realms/root/authenticate" \
--header 'X-OpenAM-Username: amadmin' \
--header "X-OpenAM-Password: ${AM_ADMIN_PASSWORD}" \
--header 'Accept-API-Version: resource=2.0, protocol=1.0' \
--cookie-jar $COOKIE_FILE

curl --fail --insecure --location --request POST "${AM_URL}/json/cache?_action=clear" \
--header 'Accept-API-Version: resource=1.0' \
--cookie $COOKIE_FILE

REALM_ID=$(curl --fail --insecure --request POST "${AM_URL}/json/global-config/realms?_action=create" \
  --header 'accept-api-version: protocol=2.0,resource=1.0' \
  --header 'content-type: application/json' \
  --data-raw '{"name":"temp-'$(date +%s)'","active":true,"parentPath":"/","aliases":[]}' \
  --cookie $COOKIE_FILE | jq -r ._id)

curl --fail --insecure --request DELETE "${AM_URL}/json/global-config/realms/${REALM_ID}" \
  --header 'accept-api-version: protocol=2.0,resource=1.0' \
  --header 'content-type: application/json' \
  --cookie $COOKIE_FILE
