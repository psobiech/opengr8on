FROM eclipse-temurin:21 AS app-builder

ENV DEBIAN_FRONTEND=noninteractive

RUN mkdir -p /opt/build
WORKDIR /opt/build

RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

FROM app-builder AS app-deps

COPY pom.xml .
COPY modules/parent/pom.xml modules/parent/
COPY modules/common/pom.xml modules/common/
COPY modules/lib/pom.xml modules/lib/
COPY modules/parsers/pom.xml modules/parsers/
COPY modules/tftp/pom.xml modules/tftp/
COPY modules/client/pom.xml modules/client/
COPY modules/vclu/pom.xml modules/vclu/

COPY assembly/jar-with-dependencies.xml assembly/

# https://issues.apache.org/jira/browse/MDEP-689
#RUN mvn -B -T 4 dependency:go-offline
RUN mvn -B -T 4 -pl '!modules/client' compile -Dorg.slf4j.simpleLogger.defaultLogLevel=ERROR -Dmaven.test.skip=true -Dmaven.site.skip=true -Dmaven.source.skip=true -Dmaven.javadoc.skip=true

FROM app-deps AS app-build

COPY modules/tftp modules/tftp
COPY modules/common modules/common
COPY modules/lib modules/lib
COPY modules/parsers modules/parsers
COPY modules/vclu modules/vclu
COPY .git .git

RUN mvn -B -T 4 -pl '!modules/client' clean package -Dorg.slf4j.simpleLogger.defaultLogLevel=WARN -Dmaven.test.skip=true -Dmaven.site.skip=true -Dmaven.source.skip=true -Dmaven.javadoc.skip=true

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-alpine AS jre-build

RUN mkdir -p /opt/build
WORKDIR /opt/build

#COPY --from=app-build /opt/build/vclu/target/vclu.jar .

RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.net.http,java.xml,java.naming,java.management,jdk.zipfs,jdk.crypto.ec,jdk.httpserver \
#         --add-modules $(jdeps --ignore-missing-deps --print-module-deps vclu.jar),java.base,java.xml,java.naming,java.management,java.sql,java.instrument,jdk.zipfs \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /opt/build/jre

FROM --platform=$BUILDPLATFORM alpine:latest AS app-runtime

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"

RUN mkdir -p /opt/docker/runtime
WORKDIR /opt/docker

COPY --from=jre-build /opt/build/jre $JAVA_HOME
COPY --from=app-build /opt/build/modules/vclu/target/vclu-jar-with-dependencies.jar /opt/docker/vclu.jar
#COPY runtime .

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
