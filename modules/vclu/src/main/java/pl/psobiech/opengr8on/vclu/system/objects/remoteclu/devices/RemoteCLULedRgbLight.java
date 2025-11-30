package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.vclu.MqttClient;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttBrightnessState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttColorState.ColorMode;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttRgbwState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttState.StateEnum;
import pl.psobiech.opengr8on.vclu.mqtt.state.RgbwColor;
import pl.psobiech.opengr8on.vclu.system.RefreshContext;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static pl.psobiech.opengr8on.vclu.system.objects.VirtualObject.IFeature;
import static pl.psobiech.opengr8on.vclu.system.objects.VirtualObject.IMethod;
import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.discoveryTopic;
import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.rootTopic;

public class RemoteCLULedRgbLight implements RemoteCLUDevice, RemoteCLUAsyncDevice {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteCLULedRgbLight.class);

    public static final String DEFAULT_RGB_VALUE = "#000000";

    private static final int RAMP_TIME = 420;

    private final VirtualCLU virtualClu;

    private final RemoteCLU remoteCLU;

    private final SpecificObject object;

    private final String discoveryTopic;

    private final MqttDiscoveryLight discoveryMessage;

    private final Map<ColorEnum, MqttDiscoveryLight> keyChildDiscoveryMessages = new ConcurrentHashMap<>();

    private final Map<String, Feature> valueFeatures;

    private final RefreshContext refreshContext;

    private JsonNode lastState = null;

    public RemoteCLULedRgbLight(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        this.virtualClu = virtualClu;
        this.remoteCLU = remoteCLU;
        this.object = object;

        this.discoveryTopic = discoveryTopic(discoveryPrefix, "light", uniqueId);
        this.discoveryMessage = new MqttDiscoveryLight(
                object.getName(),
                uniqueId,
                rootTopic(clu, object),
                null, "~/set", "~/state",
                null,
                null,
                "json",
                null,
                Set.of(ColorMode.RGBW.key()),
                mqttDiscoveryDevice
        );

        final boolean hasAsyncHandlers = !asyncHandlersInstalled(LOGGER, discoveryMessage.getUniqueId(), virtualClu.getCluObject(), clu, object).isEmpty();
        this.refreshContext = new RefreshContext(!hasAsyncHandlers, this::refresh);
        refreshContext.scheduleNextRefreshNow();

        valueFeatures = object.getFeatures().stream()
                              .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        for (ColorEnum color : ColorEnum.values()) {
            if (valueFeatures.containsKey(color.feature().featureName())) {
                final MqttDiscoveryLight discoveryMessage = childLightDeviceDiscoveryMessage(clu, object, discoveryPrefix, mqttDiscoveryDevice, color.feature().featureName());

                keyChildDiscoveryMessages.put(color, discoveryMessage);
            }
        }
    }

    private static MqttDiscoveryLight childLightDeviceDiscoveryMessage(SpecificObject clu, SpecificObject object, String discoveryPrefix, MqttDiscoveryDevice cluDevice, String valueName) {
        final String colourUniqueId = "%s_%s_%s".formatted(clu.getNameOnCLU(), object.getNameOnCLU(), valueName);

        return new MqttDiscoveryLight(
                "%s_%s".formatted(object.getName(), valueName),
                colourUniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "light", colourUniqueId),
                null, "~/set", "~/state",
                null,
                null,
                "json",
                null,
                Set.of(ColorMode.BRIGHTNESS.key()),
                cluDevice
        );
    }

    @Override
    public String getName() {
        return discoveryMessage.getName();
    }

    @Override
    public void setup() {
        for (Map.Entry<ColorEnum, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
            final ColorEnum color = entry.getKey();
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
        virtualClu.getMqttClient()
                  .tryPublish(
                          discoveryTopic,
                          discoveryMessage,
                          true
                  );
    }

    private void subscribeSetStateMessages(ColorEnum color, MqttDiscoveryLight discoveryMessage) {
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

            virtualClu.getMqttClient()
                      .publish(
                              stateTopic,
                              MqttClient.parsePayload(stateNode)
                      );

            for (Map.Entry<ColorEnum, MqttDiscoveryLight> entry : keyChildDiscoveryMessages.entrySet()) {
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
        } catch (RuntimeException e) {
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

    private Optional<JsonNode> writePartialColorValue(ColorEnum color, RemoteCLU remoteCLU, byte[] bytes) {
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
        for (ColorEnum otherColor : ColorEnum.values()) {
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

                redValue = color.get(ColorEnum.RED.key()).asInt(MqttRgbwState.OFF_VALUE);
                greenValue = color.get(ColorEnum.GREEN.key()).asInt(MqttRgbwState.OFF_VALUE);
                blueValue = color.get(ColorEnum.BLUE.key()).asInt(MqttRgbwState.OFF_VALUE);
                whiteValue = color.get(ColorEnum.WHITE.key()).asInt(MqttRgbwState.OFF_VALUE);
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

        writeCluRGBValue(remoteCLU, new RGBColor(redValue, greenValue, blueValue));
        writeCluColorValue(ColorEnum.WHITE, remoteCLU, whiteValue);

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

    private void writeCluColorValue(ColorEnum color, RemoteCLU remoteCLU, int colorValue) {
        final long methodIndex = color.method().index();

        remoteCLU.remoteMethod(object, methodIndex, colorValue, RAMP_TIME);
    }

    private void writeCluRGBValue(RemoteCLU remoteCLU, RGBColor color) {
        final long methodIndex = ColorEnum.RGB.method().index();

        remoteCLU.remoteMethod(object, methodIndex, color.getRGBAsHex(), RAMP_TIME);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        return readCluRGBValue(remoteCLU)
                .map(color -> {
                    final int white = readCluColorValue(remoteCLU, ColorEnum.WHITE)
                            .orElse(MqttRgbwState.OFF_VALUE);

                    return new MqttRgbwState(color.red(), color.green(), color.blue(), white);
                })
                .map(MqttJson::asJson);
    }

    private Optional<Integer> readCluColorValue(RemoteCLU remoteCLU, ColorEnum color) {
        return Optional.of(color.featureIndex(valueFeatures))
                       .flatMap(index -> remoteCLU.remoteGet(object, index))
                       .map(luaValue -> luaValue.optint(MqttRgbwState.OFF_VALUE));
    }

    private Optional<RGBColor> readCluRGBValue(RemoteCLU remoteCLU) {
        return Optional.of(ColorEnum.RGB.featureIndex(valueFeatures))
                       .flatMap(index -> remoteCLU.remoteGet(object, index))
                       .map(luaValue -> luaValue.optjstring(DEFAULT_RGB_VALUE))
                       .flatMap(RGBColor::parse);
    }

    private static TextNode createStateValueNode(boolean isOn) {
        return new TextNode(isOn ? StateEnum.ON.name() : StateEnum.OFF.name());
    }

    private record RGBColor(int value) {
        private RGBColor(int r, int g, int b) {
            this(((r & 255) << 16 | (g & 255) << 8 | (b & 255)));
        }

        public static Optional<RGBColor> parse(String hexColor) {
            final int value;
            try {
                value = Integer.decode(hexColor);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }

            return Optional.of(new RGBColor(value));
        }

        public int red() {
            return value >> 16 & 255;
        }

        public int green() {
            return value >> 8 & 255;
        }

        public int blue() {
            return value & 255;
        }

        public String getRGBAsHex() {
            return "#" + StringUtils.leftPad(Integer.toHexString(value), 6, '0');
        }
    }

    private enum ColorEnum {
        RED(RgbwColor.RED_KEY, Features.RED_VALUE, Methods.RED_VALUE),
        GREEN(RgbwColor.GREEN_KEY, Features.GREEN_VALUE, Methods.GREEN_VALUE),
        BLUE(RgbwColor.BLUE_KEY, Features.BLUE_VALUE, Methods.BLUE_VALUE),
        WHITE(RgbwColor.WHITE_KEY, Features.WHITE_VALUE, Methods.WHITE_VALUE),
        //
        RGB(null, Features.RGB, Methods.RGB),
        //
        ;

        private final String key;

        private final Features feature;

        private final Methods method;

        ColorEnum(String key, Features feature, Methods method) {
            this.feature = feature;
            this.method = method;
            this.key = key;
        }

        public String key() {
            return key;
        }

        public int featureIndex(Map<String, Feature> features) {
            return Optional.ofNullable(features.get(feature().featureName()))
                           .map(Feature::getIndex)
                           .orElseGet(feature::index);
        }

        public Features feature() {
            return feature;
        }

        public Methods method() {
            return method;
        }
    }

    private enum Features implements IFeature {
        RED_VALUE("RedValue", 3),
        GREEN_VALUE("GreenValue", 4),
        BLUE_VALUE("BlueValue", 5),
        WHITE_VALUE("WhiteValue", 15),
        RGB("RGB", 6),
        //
        ;

        private final String featureName;

        private final int defaultIndex;

        Features(String featureName, int defaultIndex) {
            this.featureName = featureName;
            this.defaultIndex = defaultIndex;
        }

        public String featureName() {
            return featureName;
        }

        @Override
        public int index() {
            return defaultIndex;
        }
    }

    private enum Methods implements IMethod {
        RED_VALUE("RedValue", 3),
        GREEN_VALUE("GreenValue", 4),
        BLUE_VALUE("BlueValue", 5),
        WHITE_VALUE("WhiteValue", 12),
        RGB("RGB", 6),
        //
        ;

        private final String methodName;

        private final int index;

        Methods(String methodName, int index) {
            this.methodName = methodName;
            this.index = index;
        }

        public String methodName() {
            return methodName;
        }

        @Override
        public int index() {
            return index;
        }
    }
}
