package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualDevice;

import java.util.Optional;

public interface RemoteCLUDevice extends VirtualDevice {
    Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes);

    Optional<JsonNode> readValue(RemoteCLU remoteCLU);

    void refresh();

    @Override
    default void close() {
        // NOP
    }
}
