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
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class RemoteCLULedRgbLight extends BaseRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    private final MqttDiscoveryLight discoveryMessage;

    public RemoteCLULedRgbLight(
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
                Set.of("rgbw"),
                new MqttDiscoveryDevice(clu)
        );
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> !feature1.getName().equalsIgnoreCase("Value"))
                                                         .filter(feature1 -> feature1.getName().endsWith("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        try {
            final JsonNode stateNode = ObjectMapperFactory.JSON.readTree(bytes);

            final boolean stateOn = stateNode.optional("state")
                                             .map(node -> node.asText("OFF"))
                                             .filter(state -> state.equalsIgnoreCase("ON"))
                                             .isPresent();

            final int redValue;
            final int greenValue;
            final int blueValue;
            final int whiteValue;

            if (stateOn) {
                final JsonNode color = stateNode.get("color");
                if (color != null) {
                    redValue = color.get("r").asInt(0);
                    greenValue = color.get("g").asInt(0);
                    blueValue = color.get("b").asInt(0);
                    whiteValue = color.get("w").asInt(0);
                } else {
                    redValue = 255;
                    greenValue = 255;
                    blueValue = 255;
                    whiteValue = 255;
                }
            } else {
                redValue = 0;
                greenValue = 0;
                blueValue = 0;
                whiteValue = 0;
            }

            remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get("RedValue").getIndex(), redValue));
            remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get("GreenValue").getIndex(), greenValue));
            remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get("BlueValue").getIndex(), blueValue));
            remoteCLU.remoteExecute(String.format("%s:execute(%d, %d)", object.getNameOnCLU(), valueFeatures.get("WhiteValue").getIndex(), whiteValue));

            return Optional.of(stateNode);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Map<String, Feature> valueFeatures = object.getFeatures().stream()
                                                         .filter(feature1 -> !feature1.getName().equalsIgnoreCase("Value"))
                                                         .filter(feature1 -> feature1.getName().endsWith("Value"))
                                                         .collect(Collectors.toMap(Feature::getName, UnaryOperator.identity()));

        final int redValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("RedValue").getIndex())).optint(0);
        final int greenValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("GreenValue").getIndex())).optint(0);
        final int blueValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("BlueValue").getIndex())).optint(0);
        final int whiteValue = remoteCLU.remoteExecute(String.format("%s:get(%d)", object.getNameOnCLU(), valueFeatures.get("WhiteValue").getIndex())).optint(0);

        final ObjectNode colorNode = ObjectMapperFactory.JSON.createObjectNode();
        colorNode.set("r", new IntNode(redValue));
        colorNode.set("g", new IntNode(greenValue));
        colorNode.set("b", new IntNode(blueValue));
        colorNode.set("w", new IntNode(whiteValue));

        final ObjectNode stateNode = ObjectMapperFactory.JSON.createObjectNode();
        stateNode.set("state", new TextNode(redValue > 0 || greenValue > 0 || blueValue > 0 || whiteValue > 0 ? "ON" : "OFF"));
        stateNode.set("color", colorNode);

        return Optional.of(stateNode);
    }

    @Override
    public MqttDiscoveryLight getDiscoveryMessage() {
        return discoveryMessage;
    }
}
