package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;

public abstract class BasicRemoteCLUSensor implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final VirtualCLU virtualClu;

    protected final RemoteCLU remoteCLU;

    protected final MqttDiscovery discoveryMessage;

    private final boolean hasAsyncHandlers;

    protected JsonNode lastState = null;

    private long nextRefreshAt = System.currentTimeMillis();

    public BasicRemoteCLUSensor(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            MqttDiscovery discoveryMessage
    ) {
        this.virtualClu = virtualClu;
        this.remoteCLU = remoteCLU;

        this.discoveryMessage = discoveryMessage;

        final SpecificObject virtualCluObject = virtualClu.getCluObject();

        this.hasAsyncHandlers = hasAsyncHandlersInstalled(LOGGER, discoveryMessage.getUniqueId(), virtualCluObject, clu, object);
    }

    @Override
    public void setup() {
        subscribeSetStateMessages();

        sendDiscoveryMessage();
    }

    @Override
    public void loop() {
        if (hasAsyncHandlers) {
            return;
        }

        final long now = System.currentTimeMillis();
        if (nextRefreshAt < now) {
            scheduleNextRefresh(now);

            refresh();
        }
    }

    private void scheduleNextRefresh(long now) {
        nextRefreshAt = getNextRefreshAtRandomized(nextRefreshAt, now);
    }

    @Override
    public void refresh() {
        lastState = pushState(lastState);
    }

    private void sendDiscoveryMessage() {
        final String discoveryTopic = discoveryMessage.getDiscoveryTopic();
        if (discoveryTopic == null) {
            return;
        }

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
                              LOGGER.trace("MQTT Subscribe: {} / {}", setStateTopic, ToStringUtil.toString(bytes));

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

            if (stateNode.equals(lastState)) {
                return lastState;
            }

            virtualClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(stateNode)
                      );

            return stateNode;
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);

            return lastState;
        }
    }
}
