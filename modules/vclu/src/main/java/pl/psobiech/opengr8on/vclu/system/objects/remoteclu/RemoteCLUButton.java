package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryButton;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

public class RemoteCLUButton implements RemoteCLUDevice {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final VirtualCLU currentClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final MqttDiscovery discoveryMessage;

    public RemoteCLUButton(VirtualCLU currentClu, RemoteCLU remoteCLU, SpecificObject clu, SpecificObject object, String discoveryPrefix) {
        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();

        this.currentClu = currentClu;
        this.remoteCLU = remoteCLU;
        this.object = object;

        this.discoveryMessage = new MqttDiscoveryButton(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "event", uniqueId), null, "~/state",
                "button",
                null,
                "json",
                null,
                Set.of("press"),
                new MqttDiscoveryDevice(clu)
        );
    }

    @Override
    public void setup() {
        sendDiscoveryMessage();
    }

    @Override
    public void loop() {
        // NOP
    }

    @Override
    public void refresh() {
        pushState();
    }

    private void sendDiscoveryMessage() {
        final String discoveryTopic = discoveryMessage.getDiscoveryTopic();
        if (discoveryTopic == null) {
            return;
        }

        try {
            currentClu.getMqttClient()
                      .publish(
                              discoveryTopic,
                              ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                              true
                      );
        } catch (MqttException | JsonProcessingException | RuntimeException e) {
            LOGGER.error("Could not publish discovery message for {}", discoveryMessage.getUniqueId(), e);
        }
    }

    private void pushState() {
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return;
        }

        final Optional<JsonNode> stateNode = readValue(remoteCLU);
        if (stateNode.isEmpty()) {
            return;
        }

        final String stateAsString;
        try {
            stateAsString = ObjectMapperFactory.JSON.writeValueAsString(stateNode.get());
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize state for {}", discoveryMessage.getUniqueId(), e);

            return;
        }

        currentClu.getMqttClient()
                  .tryPublish(
                          stateTopic,
                          stateAsString.getBytes(StandardCharsets.UTF_8)
                  );
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        return Optional.empty();
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final int value = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), 0)).optint(0);

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("event_type", value > 0 ? new TextNode("press") : NullNode.getInstance());

        return Optional.of(stateNode);
    }
}
