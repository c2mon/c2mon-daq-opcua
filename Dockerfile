FROM openjdk:11-jre-slim
COPY target/distribution /
ADD target/c2mon-daq-opcua-1.9.11-SNAPSHOT-dist.tar.gz c2mon-daq-opcua-1.9.11-SNAPSHOT
ADD src/test/resources/conf/simengine_pilot_edge_config.xml config.xml
EXPOSE 8913
ENV JAVA_TOOL_OPTIONS "-Dcom.sun.management.jmxremote.ssl=false \
 -Dcom.sun.management.jmxremote.authenticate=false \
 -Dcom.sun.management.jmxremote.port=8913 \
 -Dcom.sun.management.jmxremote.rmi.port=8913 \
 -Dcom.sun.management.jmxremote.host=0.0.0.0 \
 -Djava.rmi.server.hostname=0.0.0.0"
RUN ["chmod", "+x", "/docker/docker-entrypoint.sh"]
WORKDIR /c2mon-daq-opcua-1.9.11-SNAPSHOT
ENTRYPOINT ["/docker/docker-entrypoint.sh"]
