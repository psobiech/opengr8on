package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class IO extends SpecificObject {
    public IO(Long id, Long reference, String name, String nameOnCLU, String type, String description, String ipAddress, List<JsonNode> labels, List<Feature> features, List<Event> events, Clu clu, List<JsonNode> module, String classTypeId, String sourceReceiverTypeID, String number, Boolean active, Boolean statisticState, Long tfBusOrder, List<JsonNode> definedFeaturesList, List<Feature> embeddedFeaturesList, List<Event> eventsList, List<IO> iosList, List<JsonNode> scripts, List<JsonNode> peripheryList, List<JsonNode> applicationsList, List<IODefinition> modulesList, JsonNode methodsList, JsonNode methods, Boolean visible, Boolean validConfigurationOnCLU, String firmwareVersion, String firmwareType, String hardwareVersion, String hardwareType, String serialNumber, String macAddress, String cipherKeyType, String iv, String privateKey, Boolean encrypted, Boolean removed, String objectClass) {
        super(id, reference, name, nameOnCLU, type, description, ipAddress, labels, features, events, clu, module, classTypeId, sourceReceiverTypeID, number, active, statisticState, tfBusOrder, definedFeaturesList, embeddedFeaturesList, eventsList, iosList, scripts, peripheryList, applicationsList, modulesList, methodsList, methods, visible, validConfigurationOnCLU, firmwareVersion, firmwareType, hardwareVersion, hardwareType, serialNumber, macAddress, cipherKeyType, iv, privateKey, encrypted, removed, objectClass);
    }
}
