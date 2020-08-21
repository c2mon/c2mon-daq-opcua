FROM openjdk:11-jre-slim
COPY target/distribution /
ADD target/c2mon-daq-opcua-1.9.11-SNAPSHOT-dist.tar.gz c2mon-daq-opcua-1.9.11-SNAPSHOT
ADD src/test/resources/conf/edge.xml edge.xml
RUN ["chmod", "+x", "/docker/docker-entrypoint.sh"]
WORKDIR /c2mon-daq-opcua-1.9.11-SNAPSHOT
ENTRYPOINT ["/docker/docker-entrypoint.sh"]