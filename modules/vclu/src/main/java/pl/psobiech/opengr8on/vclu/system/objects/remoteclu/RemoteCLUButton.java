package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryButton;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttEvent;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;
import java.util.Set;

public class RemoteCLUButton implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final VirtualCLU virtualClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final MqttDiscovery discoveryMessage;

    private final boolean hasOnClickAsyncEventInstalled;

    public RemoteCLUButton(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        this.virtualClu = virtualClu;
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
                mqttDiscoveryDevice
        );

        final Set<String> asyncHandlersInstalled = asyncHandlersInstalled(discoveryMessage.getUniqueId(), virtualClu.getCluObject(), clu, object);
        this.hasOnClickAsyncEventInstalled = asyncHandlersInstalled.contains("OnClick");
        if (hasOnClickAsyncEventInstalled && LOGGER.isTraceEnabled()) {
            LOGGER.trace("OnClick handler is installed for {} ({}), button events will skip reading CLU value", discoveryMessage.getUniqueId(), object.getName());
        }

        final boolean hasAsyncHandlers = !asyncHandlersInstalled.isEmpty();
        if (!hasAsyncHandlers) {
            LOGGER.warn("No async handlers are installed for {} ({}), button events WILL NOT WORK", discoveryMessage.getUniqueId(), object.getName());
        }
    }

    @Override
    public void setup() {
        sendDiscoveryMessage();
    }

    private void sendDiscoveryMessage() {
        final String discoveryTopic = discoveryMessage.getDiscoveryTopic();
        if (discoveryTopic == null) {
            return;
        }

        try {
            virtualClu.getMqttClient()
                      .publish(
                              discoveryTopic,
                              ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                              true
                      );
        } catch (MqttException | JsonProcessingException | RuntimeException e) {
            LOGGER.error("Could not publish discovery message for {}", discoveryMessage.getUniqueId(), e);
        }
    }

    @Override
    public void loop() {
        // NOP
    }

    @Override
    public void scheduleRefreshNow() {
        refresh();
    }

    public void refresh() {
        pushState();
    }

    private void pushState() {
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return;
        }

        final Optional<JsonNode> stateNode;
        if (hasOnClickAsyncEventInstalled) {
            stateNode = pressEventState();
        } else {
            stateNode = readValue(remoteCLU);
        }

        if (stateNode.isEmpty()) {
            return;
        }

        virtualClu.getMqttClient()
                  .tryPublish(
                          stateTopic,
                          stateNode
                  );
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        return Optional.empty();
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final int value = remoteCLU.remoteGet(object, 0).optint(0);
        final boolean isPressed = value > 0;

        if (isPressed) {
            return pressEventState();
        }

        return Optional.empty();
    }

    private static Optional<JsonNode> pressEventState() {
        return Optional.of(MqttEvent.PRESS_AS_JSON);
    }
}
