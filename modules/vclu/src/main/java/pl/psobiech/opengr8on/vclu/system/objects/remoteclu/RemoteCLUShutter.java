package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryShutter;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryShutter.ShutterStateEnum;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttPosition;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class RemoteCLUShutter implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLUShutter.class);

    //

    private static final int SET_POSITION_METHOD = 10;

    private static final int STOP_METHOD = 3;

    //

    private static final int OPEN_POSITION = 100;

    private static final int CLOSE_POSITION = 0;

    private static final int SHUTTER_REFRESH_INTERVAL = 960;

    private final VirtualCLU virtualClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final MqttDiscoveryShutter discoveryMessage;

    private final boolean hasAsyncHandlers;

    private JsonNode lastState = null;

    private Integer expectedPosition = null;

    private long nextRefreshAt = System.currentTimeMillis();

    public RemoteCLUShutter(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        this.virtualClu = virtualClu;
        this.remoteCLU = remoteCLU;
        this.object = object;

        discoveryMessage = new MqttDiscoveryShutter(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "cover", uniqueId),
                "~/set", "~/state",
                "~/position/state", "~/position/set",
                "shutter",
                "%",
                null,
                null,
                "{ \"position\": {{ position }} }",
                mqttDiscoveryDevice
        );

        this.hasAsyncHandlers = hasAsyncHandlersInstalled(LOGGER, discoveryMessage.getUniqueId(), virtualClu.getCluObject(), clu, object);
    }

    @Override
    public void setup() {
        subscribeSetStateMessages();
        subscribeSetPositionMessages();

        sendDiscoveryMessage();
    }

    @Override
    public void loop() {
        final long now = System.currentTimeMillis();
        if (nextRefreshAt < now) {
            if (hasAsyncHandlers) {
                nextRefreshAt = Long.MAX_VALUE;
            } else {
                scheduleNextRefresh(now);
            }

            refresh();
        }
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

    private void subscribeSetPositionMessages() {
        final String setPositionTopic = discoveryMessage.getSetPositionTopic();
        if (setPositionTopic == null) {
            return;
        }

        virtualClu.getMqttClient()
                  .subscribe(
                          setPositionTopic,
                          bytes -> {
                              LOGGER.trace("MQTT Subscribe: {} / {}", setPositionTopic, ToStringUtil.toString(bytes));

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

        final String positionStateTopic = discoveryMessage.getPositionStateTopic();
        if (positionStateTopic == null) {
            return lastState;
        }

        final Optional<JsonNode> stateNodeOptional = newState != null ? Optional.of(newState) : readValue(remoteCLU);
        if (stateNodeOptional.isEmpty()) {
            return lastState;
        }

        final JsonNode stateNode = stateNodeOptional.get();
        final Optional<Integer> positionOptional = getPosition(stateNode);
        if (positionOptional.isEmpty()) {
            return lastState;
        }

        final int position = positionOptional.get();
        final ShutterStateEnum shutterState = getShutterState(position);
        if (shutterState == ShutterStateEnum.OPENING || shutterState == ShutterStateEnum.CLOSING) {
            scheduleNextRefreshIn(SHUTTER_REFRESH_INTERVAL);
        } else {
            expectedPosition = null;
        }

        try {
            virtualClu.getMqttClient()
                      .publish(
                              positionStateTopic,
                              MqttClient.parsePayload(
                                      new MqttPosition(
                                              position
                                      )
                                              .asJson()
                              )
                      );

            virtualClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(shutterState.name())
                      );

            return stateNode;
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);

            return lastState;
        }
    }

    private static Optional<Integer> getPosition(JsonNode stateNode) {
        return stateNode.optional(MqttPosition.POSITION_KEY)
                        .map(node -> node.asInt(OPEN_POSITION));
    }

    private ShutterStateEnum getShutterState(int currentPosition) {
        if (expectedPosition != null) {
            if (currentPosition < expectedPosition) {
                return ShutterStateEnum.OPENING;
            }

            if (currentPosition > expectedPosition) {
                return ShutterStateEnum.CLOSING;
            }
        }

        if (currentPosition == OPEN_POSITION) {
            return ShutterStateEnum.OPEN;
        }

        if (currentPosition == CLOSE_POSITION) {
            return ShutterStateEnum.CLOSE;
        }

        return ShutterStateEnum.STOP;
    }

    private void scheduleNextRefresh(long now) {
        nextRefreshAt = getNextRefreshAtRandomized(nextRefreshAt, now);
    }

    private void scheduleNextRefreshIn(long duration) {
        nextRefreshAt = getNextRefreshAt(nextRefreshAt, System.currentTimeMillis(), duration);
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final String stateAsString = new String(bytes, StandardCharsets.UTF_8);
        final ShutterStateEnum stateAsEnum = ShutterStateEnum.parse(stateAsString);
        if (stateAsEnum == ShutterStateEnum.STOP) {
            remoteCLU.remoteMethod(object, STOP_METHOD);

            expectedPosition = null;

            return Optional.empty();
        }

        if (stateAsEnum == ShutterStateEnum.UNKNOWN) {
            final MqttPosition positionState;
            try {
                positionState = ObjectMapperFactory.JSON.readValue(stateAsString, MqttPosition.class);
            } catch (JsonProcessingException e) {
                LOGGER.error("Could not read state from {}", stateAsString, e);

                return Optional.empty();
            }

            expectedPosition = positionState.getPosition()
                                            .orElse(OPEN_POSITION);
        } else if (stateAsEnum == ShutterStateEnum.OPEN) {
            expectedPosition = OPEN_POSITION;
        } else if (stateAsEnum == ShutterStateEnum.CLOSE) {
            expectedPosition = CLOSE_POSITION;
        }

        remoteCLU.remoteMethod(object, SET_POSITION_METHOD, expectedPosition);

        return Optional.empty();
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Optional<Feature> positionFeature = object.getFeatures().stream()
                                                        .filter(feature1 -> feature1.getName().equalsIgnoreCase("Position"))
                                                        .findAny();
        if (positionFeature.isEmpty()) {
            return Optional.empty();
        }

        final int position = remoteCLU.remoteGet(object, positionFeature.get().getIndex())
                                      .optint(OPEN_POSITION);

        return Optional.of(
                new MqttPosition(position)
                        .asJson()
        );
    }

}
