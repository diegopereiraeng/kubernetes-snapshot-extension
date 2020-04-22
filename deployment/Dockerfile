
FROM openjdk:8-jdk-alpine
# Openshift normally don't have access to above config, use below
#FROM registry.access.redhat.com/rhel7

RUN apk add --no-cache bash gawk sed grep bc coreutils

RUN mkdir -p /opt/appdynamics/MachineAgent

COPY artifacts /opt/appdynamics/MachineAgent

COPY cacerts /opt/appdynamics/MachineAgent/conf/
RUN chmod 644 /opt/appdynamics/MachineAgent/conf/cacerts

ENV MACHINE_AGENT_HOME /opt/appdynamics/MachineAgent

ENV JAVA_HOME /opt/appdynamics/MachineAgent/jre

COPY updateAnalyticsAgent.sh /updateAnalyticsAgent.sh
RUN chmod +x /updateAnalyticsAgent.sh

COPY startup.sh /startup.sh

RUN chmod +x /startup.sh
RUN chmod -R 777 /opt/appdynamics/MachineAgent

RUN chgrp -R 0 /opt && \
    chmod -R g=u /opt

WORKDIR ${MACHINE_AGENT_HOME}
CMD "/startup.sh"
