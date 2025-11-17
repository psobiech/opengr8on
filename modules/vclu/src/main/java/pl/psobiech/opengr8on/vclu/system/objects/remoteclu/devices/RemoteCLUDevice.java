package pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.vclu.system.RefreshContext;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualDevice;
import pl.psobiech.opengr8on.vclu.system.objects.remoteclu.RemoteCLU;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.Optional;

public interface RemoteCLUDevice extends VirtualDevice {
    Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes);

    Optional<JsonNode> readValue(RemoteCLU remoteCLU);

    default void scheduleRefreshNow() {
        refreshContext().ifPresent(RefreshContext::scheduleNextRefreshNow);
    }

    Optional<RefreshContext> refreshContext();

    static String discoveryTopic(String discoveryPrefix, String integration, String uniqueId) {
        return "%s/%s/%s/config".formatted(discoveryPrefix, integration, uniqueId);
    }

    static String rootTopic(SpecificObject clu, SpecificObject object) {
        return "%s/%s/%s".formatted("opengr8ton", clu.getNameOnCLU(), object.getNameOnCLU());
    }

    @Override
    default void close() {
        // NOP
    }
}
