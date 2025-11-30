#!/bin/sh

if [ -n "${OTEL_EXPORTER_OTLP_ENDPOINT}" ]; then
  export JAVA_TOOL_OPTIONS="-javaagent:/opt/docker/opentelemetry-javaagent.jar"

  export OTEL_SERVICE_NAME="openGrenton"
  export OTEL_TRACES_EXPORTER="otlp"
  export OTEL_METRICS_EXPORTER="none"
  export OTEL_LOGS_EXPORTER="none"

  #  export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
fi

exec "setpriv" "--reuid" "ubuntu" "--regid" "ubuntu" "--clear-groups" "--ambient-caps" "-all,+net_bind_service,+net_broadcast" "--inh-caps" "-all,+net_bind_service,+net_broadcast" "--no-new-privs" \
   "$JAVA_HOME/bin/java" \
   "-XX:+DisableAttachMechanism" \
   "-server" "-XX:+UseContainerSupport" \
   "-XX:+UseZGC" "-XX:+UseDynamicNumberOfGCThreads" \
   "-XX:+UseCompactObjectHeaders" \
   "--enable-native-access=ALL-UNNAMED" \
   "--sun-misc-unsafe-memory-access=allow" \
   "-XX:+ExitOnOutOfMemoryError" \
   "-Djava.net.preferIPv6Addresses=false" \
   "-Djava.net.preferIPv4Stack=true" \
   "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8" \
   "-XX:AOTCache=/opt/docker/app.aot" \
   "-jar" "/opt/docker/vclu.jar" \
   "$@"
