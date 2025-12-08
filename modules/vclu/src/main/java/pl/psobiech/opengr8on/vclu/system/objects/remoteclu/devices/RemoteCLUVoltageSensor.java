package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import org.luaj.vm2.LuaValue;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryDevice;
import pl.psobiech.opengr8on.vclu.mqtt.discovery.MqttDiscoveryNumericFloat;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU.SpecificObjectInterface;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Feature;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;

public class RemoteCLUVoltageSensor extends BasicRemoteCLUSensor implements RemoteCLUDevice {
    public RemoteCLUVoltageSensor(
            VirtualCLU virtualClu, RemoteCLU remoteCLU,
            SpecificObject clu, SpecificObject object, SpecificObjectInterface objectInterface,
            String discoveryPrefix,
            String uniqueId, MqttDiscoveryDevice mqttDiscoveryDevice
    ) {
        super(
                virtualClu, remoteCLU,
                clu, object,
                objectInterface, RemoteCLUDevice.discoveryTopic(discoveryPrefix, "sensor", uniqueId),
                new MqttDiscoveryNumericFloat(
                        object.getName(),
                        uniqueId,
                        RemoteCLUDevice.rootTopic(clu, object),
                        null, "~/state",
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
    }

    @Override
    public Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes) {
        return Optional.empty();
    }

    @Override
    public Optional<JsonNode> readValue(RemoteCLU remoteCLU) {
        return object.getFeatures().stream()
                     .filter(feature1 -> feature1.getName().equalsIgnoreCase("Value"))
                     .findAny()
                     .flatMap(feature -> remoteCLU.remoteGet(object, feature.getIndex()))
                     .filter(LuaUtil::nonNull)
                     .map(LuaValue::todouble)
                     .map(DoubleNode::new);
    }
}
