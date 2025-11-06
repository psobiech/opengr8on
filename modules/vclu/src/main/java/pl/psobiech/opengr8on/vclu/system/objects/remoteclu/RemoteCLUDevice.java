package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import com.fasterxml.jackson.databind.JsonNode;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.vclu.system.objects.VirtualDevice;

import java.util.Optional;

public interface RemoteCLUDevice extends VirtualDevice {
    Optional<JsonNode> writeValue(RemoteCLU remoteCLU, byte[] bytes);

    Optional<JsonNode> readValue(RemoteCLU remoteCLU);

    void refresh();

    default long getNextRefreshAtRandomized(long previousRefreshAt, long now) {
        return getNextRefreshAt(previousRefreshAt, now, (45_000 + RandomUtil.integer(30_000))); // 45 - 75s
    }

    default long getNextRefreshAt(long previousRefreshAt, long now, long duration) {
        if (previousRefreshAt > now) {
            return Math.min(now + duration, previousRefreshAt);
        }

        return now + duration;
    }

    @Override
    default void close() {
        // NOP
    }
}
