package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscovery;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.MqttDiscoveryNumericFloat;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class RemoteCLUTemperatureSensor extends BaseRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    private final MqttDiscovery discoveryMessage;

    public RemoteCLUTemperatureSensor(
            ExecutorService scheduler,
            VirtualCLU currentClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix
    ) {
        super(scheduler, currentClu, remoteCLU);

        this.object = object;

        final Optional<Feature> valueFeature = object.getFeatures().stream()
                                                     .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                     .findAny();
        if (valueFeature.isEmpty()) {
            this.discoveryMessage = null;

            return;
        }

        final Feature feature = valueFeature.get();

        final String uniqueId = clu.getNameOnCLU() + "_" + object.getNameOnCLU();
        this.discoveryMessage = new MqttDiscoveryNumericFloat(
                object.getName(),
                uniqueId,
                "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId), null, "~/state",
                "temperature",
                feature.getUnit(),
                null,
                null, null,
                new MqttDiscoveryDevice(clu)
        );
    }

    @Override
    public MqttDiscovery getDiscoveryMessage() {
        return discoveryMessage;
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        return Optional.empty();
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        final Optional<Feature> valueFeature = object.getFeatures().stream()
                                                     .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                                                     .findAny();

        if (valueFeature.isEmpty()) {
            return Optional.empty();
        }

        final LuaValue luaValue = remoteCLU.remoteExecute("%s:get(%d)".formatted(object.getNameOnCLU(), valueFeature.get().getIndex()));
        if (LuaUtil.isNil(luaValue)) {
            return Optional.empty();
        }

        return Optional.of(
                new DoubleNode(luaValue.todouble())
        );
    }
}
