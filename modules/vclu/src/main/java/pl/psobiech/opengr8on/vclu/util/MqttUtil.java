package pl.psobiech.opengr8on.vclu.util;

public class MqttUtil {
    private MqttUtil() {
        // NOP
    }

    public static String absoluteTopic(String rootTopic, String topic) {
        if (rootTopic != null && topic != null && topic.startsWith("~/")) {
            return rootTopic + topic.substring(1);
        }

        return topic;
    }
}
