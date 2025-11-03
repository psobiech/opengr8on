package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;

public class MqttJson {
    public ObjectNode asJson() {
        return ObjectMapperFactory.JSON.valueToTree(this);
    }
}
