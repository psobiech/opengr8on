package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;

import java.util.Optional;

public interface RemoteCLUSensor {
    Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes);

    Optional<JsonNode> readValue(RemoteCLU remoteCLU);

    MqttDiscovery getDiscoveryMessage();
}
