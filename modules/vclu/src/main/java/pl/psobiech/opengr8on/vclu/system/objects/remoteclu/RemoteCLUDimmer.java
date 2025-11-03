package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryLight;
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

        final JsonNode stateNode;
        try {
            stateNode = ObjectMapperFactory.JSON.readTree(bytes);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        final boolean stateOn = stateNode.optional("state")
                                         .map(node -> node.asText("OFF"))
                                         .filter(state -> state.equalsIgnoreCase("ON"))
                                         .isPresent();
        final int value;
        if (stateOn) {
            value = stateNode.optional("brightness")
                             .map(node -> node.asInt(0))
                             .orElse(255);

            if (stateNode instanceof ObjectNode stateObjectNode) {
                stateObjectNode.set("brightness", new IntNode(value));
            }
        } else {
            value = 0;
        }

        remoteCLU.remoteExecute(String.format("%s:set(%d, %f)", object.getNameOnCLU(), valueFeatures.get("Value").getIndex(), asFloat(value)));

        return Optional.of(stateNode);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final double value = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("Value").getIndex())).optdouble(0d);

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("state", new TextNode(value > 0 ? "ON" : "OFF"));
        stateNode.set("brightness", new IntNode((int) (value * 255)));

        return Optional.of(stateNode);
    }

    private static float asFloat(int value) {
        return value / 255f;
    }
}
