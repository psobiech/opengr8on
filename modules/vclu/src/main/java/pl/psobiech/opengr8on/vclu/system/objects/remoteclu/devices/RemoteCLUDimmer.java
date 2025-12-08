package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttBrightnessState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttColorState;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU.SpecificObjectInterface;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.discoveryTopic;
import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.rootTopic;

public class RemoteCLUDimmer extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    private final Map<String, Feature> valueFeatures;

    public RemoteCLUDimmer(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object, SpecificObjectInterface objectInterface,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        super(
                virtualClu, remoteCLU,
                clu, object, objectInterface,
                discoveryTopic(discoveryPrefix, "light", uniqueId),
                new MqttDiscoveryLight(
                        object.getName(),
                        uniqueId,
                        rootTopic(clu, object),
                        "~/set", "~/state",
                        null,
                        null,
                        "json",
                        null,
                        Set.of(MqttColorState.ColorMode.BRIGHTNESS.key()),
                        mqttDiscoveryDevice
                )
        );

        this.valueFeatures = object.getFeatures().stream()
                                   .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                   .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final MqttBrightnessState state;
        try {
            state = ObjectMapperFactory.JSON.readValue(bytes, MqttBrightnessState.class);
        } catch (IOException e) {
            LOGGER.error("Could not read MQTT brightness state", e);

            return Optional.empty();
        }

        final int value;
        if (state.isOn()) {
            value = state.getBrightness()
                         .orElse(MqttBrightnessState.MAX_VALUE);
        } else {
            value = MqttBrightnessState.OFF_VALUE;
        }

        return Optional.ofNullable(valueFeatures.get("Value"))
                       .map(Feature::getIndex)
                       .flatMap(index -> remoteCLU.remoteSet(object, index, asFloat(value)))
                       .map(ignored -> new MqttBrightnessState(value))
                       .map(MqttJson::asJson);
    }

    private static float asFloat(int value) {
        return value / (float) MqttBrightnessState.MAX_VALUE;
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        return Optional.ofNullable(valueFeatures.get("Value"))
                       .map(Feature::getIndex)
                       .flatMap(index -> remoteCLU.remoteGet(object, index))
                       .map(luaValue -> luaValue.optdouble(0.0d))
                       .map(value -> (int) (value * MqttBrightnessState.MAX_VALUE))
                       .map(MqttBrightnessState::new)
                       .map(MqttBrightnessState::asJson);
    }
}
