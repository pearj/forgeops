FROM gcr.io/forgerock-io/am-cdk:7.3.0

ARG CONFIG_PROFILE=cdk
RUN echo "\033[0;36m*** Building '${CONFIG_PROFILE}' profile ***\033[0m"
COPY  --chown=forgerock:root config-profiles/${CONFIG_PROFILE}/ /home/forgerock/openam/

# Add utility groovy classes
COPY  --chown=forgerock:root classes/ /usr/local/tomcat/webapps/am/WEB-INF/classes/

COPY --chown=forgerock:root *.sh /home/forgerock/

WORKDIR /home/forgerock

# If you want to debug AM uncomment these lines:
#ENV JPDA_TRANSPORT=dt_socket
#ENV JPDA_ADDRESS *:9009
