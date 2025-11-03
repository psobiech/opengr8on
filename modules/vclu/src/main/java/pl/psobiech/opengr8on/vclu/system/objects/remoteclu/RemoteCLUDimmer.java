package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttBrightnessState;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RemoteCLUDimmer extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    public RemoteCLUDimmer(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        super(
                virtualClu, remoteCLU,
                clu, object,
                new MqttDiscoveryLight(
                        object.getName(),
                        uniqueId,
                        "%s/%s/%s".formatted(discoveryPrefix, "light", uniqueId), "~/set", "~/state",
                        null,
                        null,
                        "json",
                        null,
                        Set.of("brightness"),
                        mqttDiscoveryDevice
                )
        );

        this.object = object;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

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

        remoteCLU.remoteSet(object, valueFeatures.get("Value").getIndex(), asFloat(value));

        return Optional.of(
                new MqttBrightnessState(
                        value
                )
                        .asJson()
        );
    }

    private static float asFloat(int value) {
        return value / (float) MqttBrightnessState.MAX_VALUE;
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final double value = remoteCLU.remoteGet(object, valueFeatures.get("Value").getIndex()).optdouble(0d);
        final MqttBrightnessState state = new MqttBrightnessState((int) (value * MqttBrightnessState.MAX_VALUE));

        return Optional.of(state.asJson());
    }
}
