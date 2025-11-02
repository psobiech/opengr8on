package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryShutter;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryShutter.StateEnum;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class RemoteCLUShutter implements RemoteCLUDevice {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLUShutter.class);

    //

    private static final int SET_POSITION_METHOD = 10;

    private static final int STOP_METHOD = 3;

    //

    private static final int OPEN_POSITION = 100;

    private static final int CLOSE_POSITION = 0;

    private final VirtualCLU currentClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final MqttDiscoveryShutter discoveryMessage;

    private String lastState = null;

    private Integer expectedPosition = null;

    private long nextRefreshAt = System.currentTimeMillis();

    public RemoteCLUShutter(
            VirtualCLU currentClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix
    ) {
        this.currentClu = currentClu;
        this.remoteCLU = remoteCLU;
        this.object = object;

        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();

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
                new MqttDiscoveryDevice(clu)
        );
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
        if (now >= nextRefreshAt) {
            scheduleNextRefresh(now);

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

        currentClu.getMqttClient()
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

        currentClu.getMqttClient()
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

        currentClu.getMqttClient()
                  .subscribe(
                          setPositionTopic,
                          bytes -> {
                              LOGGER.trace("MQTT Subscribe: {} / {}", setPositionTopic, ToStringUtil.toString(bytes));

                              lastState = pushState(lastState, writeValue(remoteCLU, bytes).orElse(null));
                          }
                  );
    }

    protected String pushState(String lastState) {
        return pushState(lastState, null);
    }

    protected String pushState(String lastState, JsonNode newState) {
        final String stateTopic = discoveryMessage.getStateTopic();
        if (stateTopic == null) {
            return lastState;
        }

        final String positionStateTopic = discoveryMessage.getPositionStateTopic();
        if (positionStateTopic == null) {
            return lastState;
        }

        try {
            final Optional<JsonNode> stateNode = newState != null ? Optional.of(newState) : readValue(remoteCLU);
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

            final int position = getPosition(stateNode.get());
            final StateEnum stateEnum = getStateEnum(position);
            LOGGER.info("State update for {}: {} / {}", discoveryMessage.getUniqueId(), stateAsString, stateEnum);

            if (stateEnum == StateEnum.OPENING || stateEnum == StateEnum.CLOSING) {
                scheduleNextRefreshIn(System.currentTimeMillis(), 1000);
            }

            if (stateAsString.equals(lastState)) {
                return lastState;
            }

            currentClu.getMqttClient()
                      .publish(
                              positionStateTopic,
                              MqttClient.parsePayload(stateAsString)
                      );

            currentClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(stateEnum.name())
                      );

            return stateAsString;
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
        }

        return lastState;
    }

    private StateEnum getStateEnum(int currentPosition) {
        if (expectedPosition == null) {
            expectedPosition = currentPosition;
        }

        if (currentPosition < expectedPosition) {
            return StateEnum.OPENING;
        }

        if (currentPosition > expectedPosition) {
            return StateEnum.CLOSING;
        }

        if (currentPosition == OPEN_POSITION) {
            return StateEnum.OPEN;
        }

        if (currentPosition == CLOSE_POSITION) {
            return StateEnum.CLOSE;
        }

        return StateEnum.STOP;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final String stateAsString = new String(bytes, StandardCharsets.UTF_8);
        final StateEnum stateAsEnum = StateEnum.parse(stateAsString);
        if (stateAsEnum == StateEnum.STOP) {
            remoteCLU.remoteExecute(String.format("%s:execute(%d)", object.getNameOnCLU(), STOP_METHOD));

            expectedPosition = null;

            return Optional.empty();
        }

        if (stateAsEnum == StateEnum.UNKNOWN) {
            final JsonNode stateNode;
            try {
                stateNode = ObjectMapperFactory.JSON.readTree(stateAsString);
            } catch (JsonProcessingException e) {
                throw new UnexpectedException(e);
            }

            expectedPosition = getPosition(stateNode);
        } else if (stateAsEnum == StateEnum.OPEN) {
            expectedPosition = OPEN_POSITION;
        } else if (stateAsEnum == StateEnum.CLOSE) {
            expectedPosition = CLOSE_POSITION;
        }

        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), SET_POSITION_METHOD, expectedPosition));

        return Optional.empty();
    }

    private static int getPosition(JsonNode stateNode) {
        return stateNode.optional("position")
                        .map(node -> node.asInt(OPEN_POSITION))
                        .orElse(OPEN_POSITION);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Optional<Feature> positionFeature = object.getFeatures().stream()
                                                        .filter(feature1 -> feature1.getName().equalsIgnoreCase("Position"))
                                                        .findAny();
        if (positionFeature.isEmpty()) {
            return Optional.empty();
        }

        final int position = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), positionFeature.get().getIndex())).optint(0);

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("position", new IntNode(position));

        return Optional.of(stateNode);
    }

    private void scheduleNextRefresh(long now) {
        scheduleNextRefreshIn(now, (45_000 + RandomUtil.integer(30_000))); // 45 - 75s
    }

    private void scheduleNextRefreshIn(long now, long duration) {
        nextRefreshAt = now + duration;
    }
}
