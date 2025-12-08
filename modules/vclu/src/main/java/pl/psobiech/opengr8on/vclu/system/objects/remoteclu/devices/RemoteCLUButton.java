package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryButton;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttEvent;
import pl.psobiech.opengr8on.vclu.system.RefreshContext;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU.SpecificObjectInterface;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;
import java.util.Set;

import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.discoveryTopic;
import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.rootTopic;

public class RemoteCLUButton implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final VirtualCLU virtualClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final SpecificObjectInterface objectInterface;

    private final String discoveryTopic;

    private final MqttDiscovery discoveryMessage;

    private final boolean hasOnClickAsyncEventInstalled;

    public RemoteCLUButton(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object, SpecificObjectInterface objectInterface,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        this.virtualClu = virtualClu;
        this.remoteCLU = remoteCLU;
        this.object = object;
        this.objectInterface = objectInterface;

        this.discoveryTopic = discoveryTopic(discoveryPrefix, "event", uniqueId);
        this.discoveryMessage = new MqttDiscoveryButton(
                object.getName(),
                uniqueId,
                rootTopic(clu, object),
                null, "~/state",
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
    public String getName() {
        return discoveryMessage.getName();
    }

    @Override
    public void setup() {
        sendDiscoveryMessage();
    }

    private void sendDiscoveryMessage() {
        try {
            virtualClu.getMqttClient()
                      .publish(
                              discoveryTopic,
                              ObjectMapperFactory.JSON.writeValueAsBytes(discoveryMessage),
                              true
                      );
        } catch (JsonProcessingException | RuntimeException e) {
            LOGGER.error("Could not publish discovery message for {}", discoveryMessage.getUniqueId(), e);
        }
    }

    @Override
    public void loop() {
        // NOP
    }

    @Override
    public Optional<RefreshContext> refreshContext() {
        return Optional.empty();
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
            stateNode = Optional.of(MqttEvent.PRESS_AS_JSON);
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
        return remoteCLU.remoteGet(object, 0)
                        .map(luaValue -> luaValue.optint(0))
                        .filter(value -> value > 0)
                        .map(value -> MqttEvent.PRESS_AS_JSON);
    }
}
