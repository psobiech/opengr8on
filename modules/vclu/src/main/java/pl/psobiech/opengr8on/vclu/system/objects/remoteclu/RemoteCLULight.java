package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttState;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RemoteCLULight extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    public RemoteCLULight(
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
                        null,
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

        final MqttState state;
        try {
            state = ObjectMapperFactory.JSON.readValue(bytes, MqttState.class);
        } catch (IOException e) {
            LOGGER.error("Could not read MQTT state", e);

            return Optional.empty();
        }

        remoteCLU.remoteSet(object, valueFeatures.get("Value").getIndex(), state.isOn() ? 1 : MqttState.OFF_VALUE);

        return Optional.of(
                state.asJson()
        );
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature -> feature.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final int value = remoteCLU.remoteGet(object, valueFeatures.get("Value").getIndex())
                                   .optint(MqttState.OFF_VALUE);

        return Optional.of(
                new MqttState(value)
                        .asJson()
        );
    }
}
