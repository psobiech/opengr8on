package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RemoteCLULedRgbLight implements RemoteCLUDevice {
    protected static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLULedRgbLight.class);

    private static final long SET_WHITE_VALUE_METHOD = 12L;

    private static final String RED_VALUE = "RedValue";

    private static final String GREEN_VALUE = "GreenValue";

    private static final String BLUE_VALUE = "BlueValue";

    private static final String WHITE_VALUE = "WhiteValue";

    private static final String RED_KEY = "r";

    private static final String GREEN_KEY = "g";

    private static final String BLUE_KEY = "b";

    private static final String WHITE_KEY = "w";

    private static final int ON_VALUE = 255;

    private static final int OFF_VALUE = 0;

    private static final int DEFAULT_VALUE = OFF_VALUE;

    private static final String STATE_OFF = "OFF";

    private static final String STATE_ON = "ON";

    protected final VirtualCLU currentClu;

    protected final RemoteCLU remoteCLU;

    private final SpecificObject object;

    protected final MqttDiscoveryLight discoveryMessage;

    private final Map<String, String> keyFeatureMap = new Hashtable<>();

    private final Map<String, MqttDiscoveryLight> keyChildDiscoveryMessages = new Hashtable<>();

    private final Map<String, Feature> valueFeatures;

    protected JsonNode lastState = null;

    private long nextRefreshAt = System.currentTimeMillis();

    public RemoteCLULedRgbLight(
            VirtualCLU currentClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix
    ) {
        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();
        final MqttDiscoveryDevice cluDevice = new MqttDiscoveryDevice(clu);

        this.currentClu = currentClu;
        this.remoteCLU = remoteCLU;
        this.object = object;

        this.discoveryMessage = new MqttDiscoveryLight(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "light", uniqueId),
                "~/set", "~/state",
                null,
                null,
                "json",
                null,
                Set.of("rgbw"),
                cluDevice
        );

        valueFeatures = object.getFeatures().stream()
                              .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        if (valueFeatures.containsKey(RED_VALUE)) {
            final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, cluDevice, RED_VALUE);

            keyChildDiscoveryMessages.put(RED_KEY, discoveryMessage);
            keyFeatureMap.put(RED_KEY, RED_VALUE);
        }

        if (valueFeatures.containsKey(GREEN_VALUE)) {
            final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, cluDevice, GREEN_VALUE);

            keyChildDiscoveryMessages.put(GREEN_KEY, discoveryMessage);
            keyFeatureMap.put(GREEN_KEY, GREEN_VALUE);
        }

        if (valueFeatures.containsKey(BLUE_VALUE)) {
            final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, cluDevice, BLUE_VALUE);

            keyChildDiscoveryMessages.put(BLUE_KEY, discoveryMessage);
            keyFeatureMap.put(BLUE_KEY, BLUE_VALUE);
        }

        if (valueFeatures.containsKey(WHITE_VALUE)) {
            final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, cluDevice, WHITE_VALUE);

            keyChildDiscoveryMessages.put(WHITE_KEY, discoveryMessage);
            keyFeatureMap.put(WHITE_KEY, WHITE_VALUE);
        }

    }

    private static MqttDiscoveryLight childLightDeviceDiscoveryMessage(SpecificObject clu, SpecificObject object, String discoveryPrefix, MqttDiscoveryDevice cluDevice, String valueName) {
        final String colourUniqueId = "%s_%s_%s".formatted(clu.getNameOnCLU(), object.getNameOnCLU(), valueName);

        return new MqttDiscoveryLight(
                "%s_%s".formatted(object.getName(), valueName),
                colourUniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "light", colourUniqueId), "~/set", "~/state",
                null,
                null,
                "json",
                null,
                Set.of("brightness"),
                cluDevice
        );
    }

    @Override
    public void setup() {
        for (Map.Entry<String, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
            final String colorKey = entry.getKey();
            final MqttDiscoveryLight childLight = entry.getValue();

            subscribeSetStateMessages(colorKey, childLight);

            sendDiscoveryMessage(childLight);
        }

        subscribeSetStateMessages(null, discoveryMessage);
        sendDiscoveryMessage(discoveryMessage);
    }

    @Override
    public void loop() {
        final long now = System.currentTimeMillis();
        if (now >= nextRefreshAt) {
            scheduleNextRefresh(now);

            refresh();
        }
    }

    private void scheduleNextRefresh(long now) {
        scheduleNextRefreshIn(now, (45_000 + RandomUtil.integer(30_000))); // 45 - 75s
    }

    private void scheduleNextRefreshIn(long now, long duration) {
        nextRefreshAt = now + duration;
    }

    @Override
    public void refresh() {
        lastState = pushState(lastState);
    }

    private void sendDiscoveryMessage(MqttDiscoveryLight discoveryMessage) {
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

    private void subscribeSetStateMessages(String colorKey, MqttDiscoveryLight discoveryMessage) {
        final String setStateTopic = discoveryMessage.getSetStateTopic();
        if (setStateTopic == null) {
            return;
        }

        currentClu.getMqttClient()
                  .subscribe(
                          setStateTopic,
                          bytes -> {
                              LOGGER.info("MQTT Subscribe: {} / {}", setStateTopic, ToStringUtil.toString(bytes));

                              if (colorKey == null) {
                                  lastState = pushState(
                                          lastState,
                                          writeValue(remoteCLU, bytes)
                                                  .orElse(null)
                                  );
                              } else {
                                  lastState = pushState(
                                          lastState,
                                          writeValueColor(colorKey, remoteCLU, bytes)
                                                  .orElse(null)
                                  );
                              }

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
            final String stateAsString;
            try {
                stateAsString = ObjectMapperFactory.JSON.writeValueAsString(stateNode);
            } catch (JsonProcessingException e) {
                LOGGER.error("Could not serialize state {} for {}", stateNode, discoveryMessage.getUniqueId(), e);

                return lastState;
            }

            if (stateNode.equals(lastState)) {
                return lastState;
            }

            currentClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(stateAsString)
                      );

            for (Map.Entry<String, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
                final MqttDiscoveryLight discoveryMessage = entry.getValue();
                if (discoveryMessage.getStateTopic() == null) {
                    continue;
                }

                final String colorKey = entry.getKey();
                final int value = stateNode.optional("color")
                                           .flatMap(jsonNode -> jsonNode.optional(colorKey))
                                           .or(() -> stateNode.optional("brightness"))
                                           .map(jsonNode -> jsonNode.asInt(DEFAULT_VALUE))
                                           .orElse(DEFAULT_VALUE);

                final ObjectNode childStateNode = ObjectMapperFactory.JSON.createObjectNode();
                childStateNode.set("state", new TextNode(value == OFF_VALUE ? "OFF" : "ON"));
                childStateNode.set("brightness", new IntNode(value));
                childStateNode.set("color_mode", new TextNode("brightness"));

                currentClu.getMqttClient()
                          .publish(
                                  discoveryMessage.getStateTopic(),
                                  MqttClient.parsePayload(childStateNode)
                          );
            }

            return stateNode;
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);
        }

        return lastState;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final JsonNode stateNode;
        try {
            stateNode = ObjectMapperFactory.JSON.readTree(bytes);

        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        return writeValue(remoteCLU, stateNode);
    }

    private Optional<JsonNode> writeValueColor(String colorKey, RemoteCLU remoteCLU, byte[] bytes) {
        final JsonNode partialStateNode;
        try {
            partialStateNode = ObjectMapperFactory.JSON.readTree(bytes);

        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        final boolean stateOn = partialStateNode.optional("state")
                                                .map(node -> node.asText(STATE_OFF))
                                                .filter(state -> state.equalsIgnoreCase(STATE_ON))
                                                .isPresent();

        final int colorValue;
        if (stateOn) {
            final Optional<JsonNode> brightnessOptional = partialStateNode.optional("brightness");
            if (brightnessOptional.isPresent()) {
                colorValue = brightnessOptional.map(JsonNode::asInt)
                                               .orElse(DEFAULT_VALUE);
            } else {
                colorValue = ON_VALUE;
            }
        } else {
            colorValue = OFF_VALUE;
        }

        final String featureName = keyFeatureMap.get(colorKey);

        final long methodIndex;
        if (featureName.equalsIgnoreCase(WHITE_VALUE)) {
            methodIndex = SET_WHITE_VALUE_METHOD;
        } else {
            methodIndex = valueFeatures.get(featureName).getIndex();
        }

        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), methodIndex, colorValue));

        if (lastState == null) {
            return Optional.empty();
        }

        final ObjectNode lastStateCopy = lastState.deepCopy();
        final Optional<JsonNode> colorOptional = lastStateCopy.optional("color");
        if (colorOptional.isEmpty() || !(colorOptional.get() instanceof ObjectNode colourObjectNode)) {
            return Optional.empty();
        }

        colourObjectNode.set(colorKey, new IntNode(colorValue));

        int colorSum = 0;
        for (String otherColorKey : keyFeatureMap.keySet()) {
            colorSum += colourObjectNode.optional(otherColorKey)
                                        .map(jsonNode -> jsonNode.asInt(DEFAULT_VALUE))
                                        .orElse(DEFAULT_VALUE);
        }

        lastStateCopy.set("state", new TextNode(colorSum == OFF_VALUE ? STATE_OFF : STATE_ON));

        return Optional.of(lastStateCopy);
    }

    private Optional<JsonNode> writeValue(RemoteCLU remoteCLU, JsonNode stateNode) {
        final boolean stateOn = stateNode.optional("state")
                                         .map(node -> node.asText(STATE_OFF))
                                         .filter(state -> state.equalsIgnoreCase(STATE_ON))
                                         .isPresent();

        final int redValue;
        final int greenValue;
        final int blueValue;
        final int whiteValue;
        if (stateOn) {
            final Optional<JsonNode> colorOptional = stateNode.optional("color");
            final Optional<JsonNode> brightnessOptional = stateNode.optional("brightness");
            if (colorOptional.isPresent()) {
                final JsonNode color = colorOptional.get();

                redValue = color.get(RED_KEY).asInt(DEFAULT_VALUE);
                greenValue = color.get(GREEN_KEY).asInt(DEFAULT_VALUE);
                blueValue = color.get(BLUE_KEY).asInt(DEFAULT_VALUE);
                whiteValue = color.get(WHITE_KEY).asInt(DEFAULT_VALUE);
            } else if (brightnessOptional.isPresent()) {
                final int brightnessValue = brightnessOptional.map(JsonNode::asInt)
                                                              .orElse(DEFAULT_VALUE);

                redValue = brightnessValue;
                greenValue = brightnessValue;
                blueValue = brightnessValue;
                whiteValue = brightnessValue;
            } else {
                redValue = ON_VALUE;
                greenValue = ON_VALUE;
                blueValue = ON_VALUE;
                whiteValue = ON_VALUE;
            }
        } else {
            redValue = OFF_VALUE;
            greenValue = OFF_VALUE;
            blueValue = OFF_VALUE;
            whiteValue = OFF_VALUE;
        }

        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get(RED_VALUE).getIndex(), redValue));
        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get(GREEN_VALUE).getIndex(), greenValue));
        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get(BLUE_VALUE).getIndex(), blueValue));

        // todo: PS remember not all features have the same id's as methods
        remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), SET_WHITE_VALUE_METHOD, whiteValue));

        if (stateNode instanceof ObjectNode stateObjectNode) {
            stateObjectNode.set("color_mode", new TextNode("rgbw"));

            final boolean isOff = redValue == OFF_VALUE && greenValue == OFF_VALUE && blueValue == OFF_VALUE && whiteValue == OFF_VALUE;
            stateObjectNode.set("state", new TextNode(isOff ? STATE_OFF : STATE_ON));
        }

        return Optional.of(stateNode);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final int redValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get(RED_VALUE).getIndex())).optint(0);
        final int greenValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get(GREEN_VALUE).getIndex())).optint(0);
        final int blueValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get(BLUE_VALUE).getIndex())).optint(0);
        final int whiteValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get(WHITE_VALUE).getIndex())).optint(0);

        final ObjectNode colorNode = ObjectMapperFactory.JSON.createObjectNode();
        colorNode.set(RED_KEY, new IntNode(redValue));
        colorNode.set(GREEN_KEY, new IntNode(greenValue));
        colorNode.set(BLUE_KEY, new IntNode(blueValue));
        colorNode.set(WHITE_KEY, new IntNode(whiteValue));

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("color", colorNode);

        final boolean isOff = redValue == OFF_VALUE && greenValue == OFF_VALUE && blueValue == OFF_VALUE && whiteValue == OFF_VALUE;
        stateNode.set("state", new TextNode(isOff ? STATE_OFF : STATE_ON));

        return Optional.of(stateNode);
    }
}
