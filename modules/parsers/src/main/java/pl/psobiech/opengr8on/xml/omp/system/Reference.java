package pl.psobiech.opengr8on.xml.omp.system;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Reference {
    private final Long reference;

    @JsonCreator
    public Reference(Long reference) {
        this.reference = reference;
    }

    public Long getReference() {
        return reference;
    }
}
