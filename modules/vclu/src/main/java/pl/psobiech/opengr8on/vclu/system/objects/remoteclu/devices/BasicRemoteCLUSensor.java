package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.system.RefreshContext;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;

public abstract class BasicRemoteCLUSensor implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final VirtualCLU virtualClu;

    protected final RemoteCLU remoteCLU;

    protected final String discoveryTopic;

    protected final MqttDiscovery discoveryMessage;

    private final RefreshContext refreshContext;

    protected JsonNode lastState = null;

    public BasicRemoteCLUSensor(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryTopic, MqttDiscovery discoveryMessage
    ) {
        this(virtualClu, remoteCLU,
             clu, object,
             discoveryTopic, discoveryMessage,
             true
        );
    }

    public BasicRemoteCLUSensor(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryTopic, MqttDiscovery discoveryMessage,
            boolean displayAsyncMissingWarning
    ) {
        this.virtualClu = virtualClu;
        this.remoteCLU = remoteCLU;

        this.discoveryTopic = discoveryTopic;
        this.discoveryMessage = discoveryMessage;

        final boolean hasAsyncHandlers = !asyncHandlersInstalled(displayAsyncMissingWarning ? LOGGER : null, discoveryMessage.getUniqueId(), virtualClu.getCluObject(), clu, object).isEmpty();
        this.refreshContext = new RefreshContext(!hasAsyncHandlers, this::refresh);
    }

    @Override
    public String getName() {
        return discoveryMessage.getName();
    }

    @Override
    public void setup() {
        subscribeSetStateMessages();

        sendDiscoveryMessage();
    }

    @Override
    public void loop() {
        refreshContext.runIfScheduled();
    }

    @Override
    public Optional<RefreshContext> refreshContext() {
        return Optional.of(refreshContext);
    }

    public void refresh() {
        lastState = pushState(lastState);
    }

    private void sendDiscoveryMessage() {
        virtualClu.getMqttClient()
                  .tryPublish(
                          discoveryTopic,
                          discoveryMessage,
                          true
                  );
    }

    private void subscribeSetStateMessages() {
        final String setStateTopic = discoveryMessage.getSetStateTopic();
        if (setStateTopic == null) {
            return;
        }

        virtualClu.getMqttClient()
                  .subscribe(
                          setStateTopic,
                          bytes -> {
                              LOGGER.trace("MQTT: {} / {}", setStateTopic, ToStringUtil.toString(bytes));

                              lastState = pushState(lastState, writeValue(remoteCLU, bytes).orElse(null));
                          }
                  );
    }

    protected JsonNode pushState(JsonNode lastState) {
        return pushState(lastState, null);
    }

    protected JsonNode pushState(JsonNode lastState, JsonNode newState) {
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return lastState;
        }

        try {
            final Optional<JsonNode> stateNodeOptional = newState != null ? Optional.of(newState) : readValue(remoteCLU);
            if (stateNodeOptional.isEmpty()) {
                return lastState;
            }

            final JsonNode stateNode = stateNodeOptional.get();

            virtualClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(stateNode)
                      );

            return stateNode;
        } catch (RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);

            return lastState;
        }
    }
}
