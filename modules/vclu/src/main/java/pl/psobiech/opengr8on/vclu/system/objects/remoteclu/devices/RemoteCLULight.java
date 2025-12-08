package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryLight;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttColorState;
import pl.psobiech.opengr8on.vclu.mqtt.state.MqttState;
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

public class RemoteCLULight extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    private final Map<String, Feature> valueFeatures;

    public RemoteCLULight(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object, SpecificObjectInterface objectInterface,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        super(
                virtualClu, remoteCLU,
                clu, object,
                objectInterface, discoveryTopic(discoveryPrefix, "light", uniqueId),
                new MqttDiscoveryLight(
                        object.getName(),
                        uniqueId,
                        rootTopic(clu, object),
                        "~/set", "~/state",
                        null,
                        null,
                        "json",
                        null,
                        Set.of(MqttColorState.ColorMode.ON_OFF.key()),
                        mqttDiscoveryDevice
                )
        );

        this.valueFeatures = object.getFeatures().stream()
                                   .filter(feature -> feature.getName().equalsIgnoreCase("Value"))
                                   .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final MqttState state;
        try {
            state = ObjectMapperFactory.JSON.readValue(bytes, MqttState.class);
        } catch (IOException e) {
            LOGGER.error("Could not read MQTT state", e);

            return Optional.empty();
        }

        return Optional.ofNullable(valueFeatures.get("Value"))
                       .map(Feature::getIndex)
                       .flatMap(index -> remoteCLU.remoteSet(object, index, state.isOn() ? 1 : MqttState.OFF_VALUE))
                       .map(ignored -> state.asJson());
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        return Optional.ofNullable(valueFeatures.get("Value"))
                       .map(Feature::getIndex)
                       .flatMap(index -> remoteCLU.remoteGet(object, index))
                       .map(luaValue -> luaValue.optint(MqttState.OFF_VALUE))
                       .map(MqttState::new)
                       .map(MqttJson::asJson);
    }
}
