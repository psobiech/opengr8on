package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RemoteCLUDimmer extends BaseRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    private final MqttDiscoveryLight discoveryMessage;

    public RemoteCLUDimmer(
            ExecutorService scheduler,
            VirtualCLU currentClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix
    ) {
        super(scheduler, currentClu, remoteCLU);

        this.object = object;

        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();
        this.discoveryMessage = new MqttDiscoveryLight(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "light", uniqueId), "~/set", "~/state",
                null,
                null,
                "json",
                null,
                null,
                new MqttDiscoveryDevice(clu)
        );
    }

    @Override
    public MqttDiscoveryLight getDiscoveryMessage() {
        return discoveryMessage;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final JsonNode jsonNode;
        try {
            jsonNode = ObjectMapperFactory.JSON.readTree(bytes);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        final boolean stateOn = jsonNode.get("state").asText("OFF")
                                        .equalsIgnoreCase("ON");

        final int value;
        if (stateOn) {
            value = 1;
        } else {
            value = 0;
        }

        remoteCLU.remoteExecute(String.format("%s:set(%d, %d)", object.getNameOnCLU(), valueFeatures.get("Value").getIndex(), value));

        return Optional.of(jsonNode);
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final int value = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("Value").getIndex())).optint(0);

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("state", new TextNode(value > 0 ? "ON" : "OFF"));

        return Optional.of(stateNode);
    }
}
