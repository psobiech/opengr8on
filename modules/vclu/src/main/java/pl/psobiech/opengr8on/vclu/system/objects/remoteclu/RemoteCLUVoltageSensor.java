package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryNumericFloat;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;

public class RemoteCLUVoltageSensor extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    private final SpecificObject object;

    public RemoteCLUVoltageSensor(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        super(
                virtualClu, remoteCLU,
                clu, object,
                new MqttDiscoveryNumericFloat(
                        object.getName(),
                        uniqueId,
                        "%s/%s/%s".formatted(discoveryPrefix, "sensor", uniqueId), null, "~/state",
                        "voltage",
                        object.getFeatures().stream()
                              .filter(feature1 -> feature1.getName().equalsIgnoreCase("value"))
                              .findAny()
                              .map(Feature::getUnit)
                              .orElse("V"),
                        null,
                        null, null,
                        mqttDiscoveryDevice
                ),
                false
        );

        this.object = object;
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

        final LuaValue luaValue = remoteCLU.remoteGet(object, valueFeature.get().getIndex());
        if (LuaUtil.isNil(luaValue)) {
            return Optional.empty();
        }

        return Optional.of(
                new DoubleNode(luaValue.todouble())
        );
    }
}
