package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public enum SpecificObjectClass {
    UNSUPPORTED(null),
    //
    INPUT("Input"),
    OUTPUT("Output"),
    CONTAINER("Container"),
    SCRIPT("Script"),
    CLU("CLU"),
    MOBILE_INTERFACE("com.grenton.om.mobile.gui.MobileInterfaceTreeObject"),
    //
    ;

    private final static Map<String, SpecificObjectClass> RAW_VALUE_MAP;

    static {
        final Map<String, SpecificObjectClass> labelValueMap = new HashMap<>();
        for (SpecificObjectClass value : values()) {
            if (value.rawValue == null) {
                continue;
            }

            labelValueMap.put(value.rawValue.toLowerCase(), value);
        }

        RAW_VALUE_MAP = Collections.unmodifiableMap(labelValueMap);
    }

    private final String rawValue;

    SpecificObjectClass(String rawValue) {
        this.rawValue = rawValue;
    }

    public static SpecificObjectClass fromRawValue(String rawValue) {
        if (rawValue == null) {
            return UNSUPPORTED;
        }

        return RAW_VALUE_MAP.getOrDefault(rawValue.toLowerCase(), UNSUPPORTED);
    }
}
