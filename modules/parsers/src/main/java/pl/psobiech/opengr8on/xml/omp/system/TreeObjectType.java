package pl.psobiech.opengr8on.xml.omp.system;

public enum TreeObjectType {
    UNSUPPORTED,
    //
    INPUT,
    OUTPUT,
    CLU,
    HARDWARE_PERIPHERY,
    //
    OBJECT_MANAGER,
    //
    SCRIPT,
    //
    CONTAINER,
    SCRIPT_CONTAINER,
    APPLICATIONS_CONTAINER,
    //
    VISUAL_BUILDER,
    //
    MYGRENTON_INTERFACE,
    MYGRENTON_INTERFACES,
    //
    ;

    public static TreeObjectType fromString(String label) {
        if (label == null) {
            return UNSUPPORTED;
        }

        for (TreeObjectType value : values()) {
            if (value.name().equalsIgnoreCase(label)) {
                return value;
            }
        }

        return UNSUPPORTED;
    }
}
