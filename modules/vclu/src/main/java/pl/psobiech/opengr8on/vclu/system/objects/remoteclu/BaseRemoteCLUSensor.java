package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public abstract class BaseRemoteCLUSensor implements RemoteCLUDevice {
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final ExecutorService scheduler;

    private final VirtualCLU currentClu;

    private final RemoteCLU remoteCLU;

    public BaseRemoteCLUSensor(ExecutorService scheduler, VirtualCLU currentClu, RemoteCLU remoteCLU) {
        this.scheduler = scheduler;

        this.currentClu = currentClu;
        this.remoteCLU = remoteCLU;
    }

    @Override
    public void register() {
        sendDiscoveryMessage();
        subscribeCommandMessages();
        scheduleStatePolling();
    }

    private void sendDiscoveryMessage() {
        final MqttDiscovery discoveryMessage = getDiscoveryMessage();
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

    private void subscribeCommandMessages() {
        final MqttDiscovery discoveryMessage = getDiscoveryMessage();
        final String commandTopic = discoveryMessage.getCommandTopic();
        if (commandTopic == null) {
            return;
        }

        currentClu.getMqttClient()
                  .subscribe(
                          commandTopic,
                          bytes -> {
                              LOGGER.trace("MQTT Subscribe: {} / {}", commandTopic, ToStringUtil.toString(bytes));

                              try {
                                  Optional<JsonNode> stateNode = writeValue(remoteCLU, bytes);
                                  if (stateNode.isEmpty()) {
                                      stateNode = readValue(remoteCLU);
                                  }

                                  if (stateNode.isPresent()) {
                                      currentClu.getMqttClient()
                                                .publish(
                                                        discoveryMessage.getStateTopic(),
                                                        ObjectMapperFactory.JSON.writeValueAsBytes(stateNode.get())
                                                );
                                  }
                              } catch (MqttException | JsonProcessingException | RuntimeException e) {
                                  LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
                              }
                          }
                  );
    }

    private void scheduleStatePolling() {
        final MqttDiscovery discoveryMessage = getDiscoveryMessage();
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return;
        }

        scheduler.execute(() -> {
            String lastState = null;
            while (!Thread.currentThread().isInterrupted()) {
                ThreadUtil.sleepRandomized(60_000L, 45_000);

                lastState = pushState(lastState);
            }
        });
    }

    protected String pushState(String lastState) {
        final MqttDiscovery discoveryMessage = getDiscoveryMessage();
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return lastState;
        }

        try {
            final Optional<JsonNode> stateNode = readValue(remoteCLU);
            if (stateNode.isEmpty()) {
                return lastState;
            }

            final String stateAsString;
            try {
                stateAsString = ObjectMapperFactory.JSON.writeValueAsString(stateNode.get());
            } catch (JsonProcessingException e) {
                LOGGER.error("Could not serialize state for {}", discoveryMessage.getUniqueId(), e);

                return lastState;
            }

            if (stateAsString.equals(lastState)) {
                return lastState;
            }
            lastState = stateAsString;

            currentClu.getMqttClient()
                      .publish(
                              stateTopic,
                              stateAsString.getBytes(StandardCharsets.UTF_8)
                      );
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
        }
        return lastState;
    }
}
