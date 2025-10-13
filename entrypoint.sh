#!/bin/sh

exec "setpriv" "--reuid" "ubuntu" "--regid" "ubuntu" "--clear-groups" "--ambient-caps" "-all,+net_bind_service,+net_broadcast" "--inh-caps" "-all,+net_bind_service,+net_broadcast" "--no-new-privs" \
   "$JAVA_HOME/bin/java" \
   "-XX:+DisableAttachMechanism" \
   "-server" "-Xshare:off" "-XX:+UseContainerSupport" "-XX:+UseZGC" "-XX:+UseDynamicNumberOfGCThreads" \
   "-XX:+ExitOnOutOfMemoryError" \
   "-Djava.net.preferIPv6Addresses=false" \
   "-Djava.net.preferIPv4Stack=true" \
   "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8" \
   "-jar" "/opt/docker/vclu.jar" \
   "$@"
