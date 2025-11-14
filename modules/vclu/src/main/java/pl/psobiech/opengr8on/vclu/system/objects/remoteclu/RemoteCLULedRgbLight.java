package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttBrightnessState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttColorState.ColorMode;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttRgbwState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttState.StateEnum;
import pl.psobiech.opengr8on.vclu.mqtt.state.RgbwColor;
import pl.psobiech.opengr8on.vclu.system.RefreshContext;
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

public class RemoteCLULedRgbLight implements RemoteCLUDevice, RemoteCLUAsyncDevice {
    protected static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLULedRgbLight.class);

    private static final long SET_WHITE_VALUE_METHOD_ID = 12L;

    protected final VirtualCLU virtualClu;

    protected final RemoteCLU remoteCLU;

    private final SpecificObject object;

    protected final MqttDiscoveryLight discoveryMessage;

    private final Map<Color, MqttDiscoveryLight> keyChildDiscoveryMessages = new Hashtable<>();

    private final Map<String, Feature> valueFeatures;

    private final RefreshContext refreshContext;

    protected JsonNode lastState = null;

    public RemoteCLULedRgbLight(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        this.virtualClu = virtualClu;
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
                Set.of(ColorMode.RGBW.key()),
                mqttDiscoveryDevice
        );

        final boolean hasAsyncHandlers = !asyncHandlersInstalled(LOGGER, discoveryMessage.getUniqueId(), virtualClu.getCluObject(), clu, object).isEmpty();
        this.refreshContext = new RefreshContext(!hasAsyncHandlers, this::refresh);

        valueFeatures = object.getFeatures().stream()
                              .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        for (Color color : Color.values()) {
            if (valueFeatures.containsKey(color.featureName())) {
                final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, mqttDiscoveryDevice, color.featureName());

                keyChildDiscoveryMessages.put(color, discoveryMessage);
            }
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
                Set.of(ColorMode.BRIGHTNESS.key()),
                cluDevice
        );
    }

    @Override
    public void setup() {
        for (Map.Entry<Color, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
            final Color color = entry.getKey();
            final MqttDiscoveryLight childLight = entry.getValue();

            subscribeSetStateMessages(color, childLight);

            sendDiscoveryMessage(childLight);
        }

        subscribeSetStateMessages(null, discoveryMessage);
        sendDiscoveryMessage(discoveryMessage);
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

    private void sendDiscoveryMessage(MqttDiscoveryLight discoveryMessage) {
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

    private void subscribeSetStateMessages(Color color, MqttDiscoveryLight discoveryMessage) {
        final String setStateTopic = discoveryMessage.getSetStateTopic();
        if (setStateTopic == null) {
            return;
        }

        virtualClu.getMqttClient()
                  .subscribe(
                          setStateTopic,
                          bytes -> {
                              LOGGER.trace("MQTT: {} / {}", setStateTopic, ToStringUtil.toString(bytes));

                              final Optional<JsonNode> stateNode;
                              if (color == null) {
                                  stateNode = writeValue(remoteCLU, bytes);

                              } else {
                                  stateNode = writePartialColorValue(color, remoteCLU, bytes);
                              }

                              lastState = pushState(
                                      lastState,
                                      stateNode
                                              .orElse(null)
                              );
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

            for (Map.Entry<Color, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
                final MqttDiscoveryLight discoveryMessage = entry.getValue();
                if (discoveryMessage.getStateTopic() == null) {
                    continue;
                }

                final String colorKey = entry.getKey().key();
                final int value = stateNode.optional(MqttRgbwState.COLOR_KEY)
                                           .flatMap(jsonNode -> jsonNode.optional(colorKey))
                                           .or(() -> stateNode.optional(MqttRgbwState.BRIGHTNESS_KEY))
                                           .map(valueNode -> valueNode.asInt(MqttRgbwState.OFF_VALUE))
                                           .orElse(MqttRgbwState.OFF_VALUE);

                final ObjectNode childStateNode = new MqttBrightnessState(value)
                        .asJson();

                virtualClu.getMqttClient()
                          .publish(
                                  discoveryMessage.getStateTopic(),
                                  MqttClient.parsePayload(childStateNode)
                          );
            }

            return stateNode;
        } catch (MqttException | RuntimeException e) {
            LOGGER.error("Could not publish state update message for {}", discoveryMessage.getUniqueId(), e);

            return lastState;
        }
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final JsonNode newState;
        try {
            newState = ObjectMapperFactory.JSON.readTree(bytes);
        } catch (IOException e) {
            LOGGER.warn("Could not parse state update message for {} ({})", discoveryMessage.getUniqueId(), HexUtil.asString(bytes), e);

            return Optional.empty();
        }

        return writeValue(remoteCLU, newState);
    }

    private Optional<JsonNode> writePartialColorValue(Color color, RemoteCLU remoteCLU, byte[] bytes) {
        final MqttRgbwState newState;
        try {
            newState = ObjectMapperFactory.JSON.readValue(bytes, MqttRgbwState.class);
        } catch (IOException e) {
            LOGGER.warn("Could not parse partial state update message for {} ({})", discoveryMessage.getUniqueId(), HexUtil.asString(bytes), e);

            return Optional.empty();
        }

        final int colorValue;
        if (newState.isOn()) {
            colorValue = newState.getBrightness()
                                 .orElse(MqttRgbwState.MAX_VALUE);
        } else {
            colorValue = MqttRgbwState.OFF_VALUE;
        }

        writeCluColorValue(color, remoteCLU, colorValue);

        if (lastState == null) {
            return Optional.empty();
        }

        final ObjectNode lastStateCopy = lastState.deepCopy();
        final Optional<JsonNode> colorOptional = lastStateCopy.optional(MqttRgbwState.COLOR_KEY);
        if (colorOptional.isEmpty() || !(colorOptional.get() instanceof ObjectNode colourObjectNode)) {
            return Optional.empty();
        }

        colourObjectNode.set(color.key(), new IntNode(colorValue));

        int colorSum = 0;
        for (Color otherColor : Color.values()) {
            colorSum += colourObjectNode.optional(otherColor.key())
                                        .map(jsonNode -> jsonNode.asInt(MqttRgbwState.OFF_VALUE))
                                        .orElse(MqttRgbwState.OFF_VALUE);
        }

        lastStateCopy.set(MqttRgbwState.STATE_KEY, createStateValueNode(colorSum != MqttRgbwState.OFF_VALUE));

        return Optional.of(lastStateCopy);
    }

    private Optional<JsonNode> writeValue(RemoteCLU remoteCLU, JsonNode stateNode) {
        final int redValue;
        final int greenValue;
        final int blueValue;
        final int whiteValue;
        if (isStateOn(stateNode)) {
            final Optional<JsonNode> colorOptional = stateNode.optional(MqttRgbwState.COLOR_KEY);
            final Optional<JsonNode> brightnessOptional = stateNode.optional(MqttRgbwState.BRIGHTNESS_KEY);
            if (colorOptional.isPresent()) {
                final JsonNode color = colorOptional.get();

                redValue = color.get(Color.RED.key()).asInt(MqttRgbwState.OFF_VALUE);
                greenValue = color.get(Color.GREEN.key()).asInt(MqttRgbwState.OFF_VALUE);
                blueValue = color.get(Color.BLUE.key()).asInt(MqttRgbwState.OFF_VALUE);
                whiteValue = color.get(Color.WHITE.key()).asInt(MqttRgbwState.OFF_VALUE);
            } else if (brightnessOptional.isPresent()) {
                final int brightnessValue = brightnessOptional.map(JsonNode::asInt)
                                                              .orElse(MqttRgbwState.OFF_VALUE);

                redValue = brightnessValue;
                greenValue = brightnessValue;
                blueValue = brightnessValue;
                whiteValue = brightnessValue;
            } else {
                redValue = MqttRgbwState.MAX_VALUE;
                greenValue = MqttRgbwState.MAX_VALUE;
                blueValue = MqttRgbwState.MAX_VALUE;
                whiteValue = MqttRgbwState.MAX_VALUE;
            }
        } else {
            redValue = MqttRgbwState.OFF_VALUE;
            greenValue = MqttRgbwState.OFF_VALUE;
            blueValue = MqttRgbwState.OFF_VALUE;
            whiteValue = MqttRgbwState.OFF_VALUE;
        }

        writeCluColorValue(Color.RED, remoteCLU, redValue);
        writeCluColorValue(Color.GREEN, remoteCLU, greenValue);
        writeCluColorValue(Color.BLUE, remoteCLU, blueValue);
        writeCluColorValue(Color.WHITE, remoteCLU, whiteValue);

        return Optional.of(
                new MqttRgbwState(redValue, greenValue, blueValue, whiteValue)
                        .asJson()
        );
    }

    private static boolean isStateOn(JsonNode stateNode) {
        return stateNode.optional(MqttRgbwState.STATE_KEY)
                        .map(node -> node.asText(StateEnum.OFF.name()))
                        .filter(state -> state.equalsIgnoreCase(StateEnum.ON.name()))
                        .isPresent();
    }

    private void writeCluColorValue(Color color, RemoteCLU remoteCLU, int colorValue) {
        final String featureName = color.featureName();

        final long methodIndex;
        if (featureName.equalsIgnoreCase(Color.WHITE.featureName())) {
            methodIndex = SET_WHITE_VALUE_METHOD_ID;
        } else {
            methodIndex = valueFeatures.get(featureName).getIndex();
        }

        remoteCLU.remoteMethod(object, methodIndex, colorValue);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final int redValue = readCluColorValue(remoteCLU, Color.RED.featureName());
        final int greenValue = readCluColorValue(remoteCLU, Color.GREEN.featureName());
        final int blueValue = readCluColorValue(remoteCLU, Color.BLUE.featureName());
        final int whiteValue = readCluColorValue(remoteCLU, Color.WHITE.featureName());

        return Optional.of(
                new MqttRgbwState(redValue, greenValue, blueValue, whiteValue)
                        .asJson()
        );
    }

    private int readCluColorValue(RemoteCLU remoteCLU, String colorKey) {
        return remoteCLU.remoteGet(object, valueFeatures.get(colorKey).getIndex())
                        .optint(MqttRgbwState.OFF_VALUE);
    }

    private static TextNode createStateValueNode(boolean isOn) {
        return new TextNode(isOn ? StateEnum.ON.name() : StateEnum.OFF.name());
    }

    private enum Color {
        RED(RgbwColor.RED_KEY, "RedValue"),
        GREEN(RgbwColor.GREEN_KEY, "GreenValue"),
        BLUE(RgbwColor.BLUE_KEY, "BlueValue"),
        WHITE(RgbwColor.WHITE_KEY, "WhiteValue"),
        //
        ;

        private final String featureName;

        private final String key;

        Color(String key, String featureName) {
            this.key = key;
            this.featureName = featureName;
        }

        public String featureName() {
            return featureName;
        }

        public String key() {
            return key;
        }
    }
}
