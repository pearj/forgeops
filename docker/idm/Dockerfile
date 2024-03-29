# Note: M5 integration with AM currently not working
FROM gcr.io/forgerock-io/idm-cdk:7.3.0

COPY debian-buster-sources.list /etc/apt/sources.list

USER root
RUN apt-get update \
        && apt-get install -y rsync jq \
        && apt-get clean \
        && rm -r /var/lib/apt/lists /var/cache/apt/archives
USER forgerock
# Harden IDM by removing the Felix OSGI Console. Unless you are a ForgeRock developer, the
# console is rarely required. Your configuration should NOT include conf/felix.webconsole.json
RUN rm -f bundle/org.apache.felix.webconsole*.jar  && \
    rm -f bundle/openidm-felix-webconsole-*.jar

# Remove this once https://bugster.forgerock.org/jira/browse/OPENIDM-16100 is integrated
# This sets the RAM based on cgroups to 65% of the container memory
ENV JAVA_OPTS -XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=65 -XX:MaxTenuringThreshold=1 -Djava.security.egd=file:/dev/urandom -XshowSettings:vm  -XX:+PrintFlagsFinal

ARG CONFIG_PROFILE=cdk
RUN echo "\033[0;36m*** Building '${CONFIG_PROFILE}' profile ***\033[0m"
COPY --chown=forgerock:root config-profiles/${CONFIG_PROFILE}/ /opt/openidm
COPY --chown=forgerock:root . /opt/openidm

# Add merge the translation files together
RUN jq -s '.[0] * .[1]' ui/admin/default/locales/en/translation.json ui/admin/extension/locales/en/translation.merge.json > ui/admin/extension/locales/en/translation.json
