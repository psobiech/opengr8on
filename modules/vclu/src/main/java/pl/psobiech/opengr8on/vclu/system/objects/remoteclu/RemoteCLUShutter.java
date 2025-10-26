package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryShutter;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class RemoteCLUShutter extends BaseRemoteCLUSensor implements RemoteCLUDevice {
    private static final int SET_POSITION_METHOD = 10;

    private final MqttDiscoveryShutter discoveryMessage;

    private final SpecificObject object;

    public RemoteCLUShutter(
            ExecutorService scheduler,
            VirtualCLU currentClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix
    ) {
        super(scheduler, currentClu, remoteCLU);

        this.object = object;

        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();
        this.discoveryMessage = new MqttDiscoveryShutter(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "cover", uniqueId),
                "~/set", "~/state", "~/state", "~/set",
                "shutter",
                "%",
                null,
                null,
                "{ \"position\": {{ position }} }",
                "{ \"position\": 100 }",
                "{ \"position\": 0 }",
                new MqttDiscoveryDevice(clu)
        );
    }

    @Override
    public MqttDiscoveryShutter getDiscoveryMessage() {
        return discoveryMessage;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final String stateAsString = new String(bytes, StandardCharsets.UTF_8);
        if (stateAsString.equalsIgnoreCase("STOP")) {
            remoteCLU.remoteExecute(String.format("%s:execute(%d)", object.getNameOnCLU(), 3));
        } else {
            final int position;
            if (stateAsString.equalsIgnoreCase("OPEN")) {
                position = 100;
            } else if (stateAsString.equalsIgnoreCase("CLOSE")) {
                position = 0;
            } else {
                final JsonNode stateNode;
                try {
                    stateNode = ObjectMapperFactory.JSON.readTree(stateAsString);
                } catch (JsonProcessingException e) {
                    throw new UnexpectedException(e);
                }

                position = stateNode.optional("position")
                                    .map(node -> node.asInt(0))
                                    .orElse(0);
            }

            remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), SET_POSITION_METHOD, position));

            final ObjectNode positionNode = ObjectMapperFactory.JSON.createObjectNode();
            positionNode.set("position", new IntNode(position));

            return Optional.of(positionNode);
        }

        return Optional.empty();
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
}
