package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum SpecificObjectType {
    UNSUPPORTED(null),
    //
    CLU("CLU"),
    SCRIPT("SCRIPT"),
    CONTAINER("CONTAINER"),
    //
    DIMM("DIMM"),
    BUTTON("BUTTON"),
    DIN("DIN"),
    DOUT("DOUT"),
    //
    PANEL("PANEL"),
    PANEL_PAGE("PANEL_PAGE"),
    PANEL_BUTTON("PANEL_BUTTON"),
    PANEL_TEMPERATURE("PANELSENSTEMP"),
    PANEL_LUMINOSITY("PANELSENSLIGHT"),
    //
    ANALOG_OUT("AnalogOUT"),
    ANALOG_IN("AnalogIN"),
    //
    POWER_SUPPLY_VOLTAGE("PowerSupplyVoltage"),
    //
    LED_RGB("LEDRGB"),
    //
    ROLLER_SHUTTER("ROLLER_SHUTTER"),
    //
    ZWAVE_CONFIG("ZWAVE_CONFIG"),
    ZWAVE_FAKRO("ZWAVE_FAKRO"),
    ZWAVE_UNKNOWN_MODULE("ZWAVE_UNKNOWN_MODULE"),
    //
    ;

    private final static Map<String, SpecificObjectType> RAW_VALUE_MAP;

    static {
        final Map<String, SpecificObjectType> labelValueMap = new HashMap<>();
        for (SpecificObjectType value : values()) {
            if (value.rawValue == null) {
                continue;
            }

            labelValueMap.put(value.rawValue.toLowerCase(), value);
        }

        RAW_VALUE_MAP = Collections.unmodifiableMap(labelValueMap);
    }

    private final String rawValue;

    SpecificObjectType(String rawValue) {
        this.rawValue = rawValue;
    }

    public static SpecificObjectType fromRawValue(String rawValue) {
        if (rawValue == null) {
            return UNSUPPORTED;
        }

        return RAW_VALUE_MAP.getOrDefault(rawValue.toLowerCase(), UNSUPPORTED);
    }
}
