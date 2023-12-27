FROM eclipse-temurin:21 AS app-builder

ENV DEBIAN_FRONTEND=noninteractive

RUN mkdir -p /opt/build
WORKDIR /opt/build

RUN apt update && \
    apt install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM app-builder AS app-build

COPY tftp/pom.xml tftp/
COPY common/pom.xml common/
COPY lib/pom.xml lib/
COPY client/pom.xml client/
COPY vclu/pom.xml vclu/
COPY pom.xml .

COPY vclu/assembly/jar-with-dependencies.xml vclu/assembly/

# https://issues.apache.org/jira/browse/MDEP-689
#RUN mvn dependency:go-offline
RUN mvn install

COPY tftp tftp
COPY common common
COPY lib lib
COPY client client
COPY vclu vclu

RUN mvn package

FROM --platform=$BUILDPLATFORM eclipse-temurin:21 AS jre-build

RUN mkdir -p /opt/build
WORKDIR /opt/build

#COPY --from=app-build /opt/build/vclu/target/vclu.jar .

RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.xml,java.naming,java.management,jdk.zipfs \
#         --add-modules $(jdeps --ignore-missing-deps --print-module-deps vclu.jar),java.base,java.xml,java.naming,java.management,java.sql,java.instrument,jdk.zipfs,jdk.unsupported \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /opt/build/jre

FROM --platform=$BUILDPLATFORM ubuntu:22.04 AS app-runtime

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"

RUN mkdir -p /opt/docker
WORKDIR /opt/docker

COPY --from=jre-build /opt/build/jre $JAVA_HOME
COPY --from=app-build /opt/build/vclu/target/vclu.jar /opt/docker
COPY runtime .

ENTRYPOINT [ \
  "java", \
  "-XX:+DisableAttachMechanism", \
  "-server", "-Xshare:off", "-XX:+UseContainerSupport", "-XX:+UseZGC", "-XX:+UseDynamicNumberOfGCThreads", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.net.preferIPv6Addresses=false", \
#  "-Djava.net.preferIPv4Stack=true", \
  "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8", \
  "-jar", "vclu.jar" \
]
