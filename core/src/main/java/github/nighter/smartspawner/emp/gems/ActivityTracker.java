package github.nighter.smartspawner.emp.gems;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityTracker {
    private final ConcurrentHashMap<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public void markActive(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
    }

    public boolean isActive(UUID uuid, long withinMillis) {
        Long last = lastActivity.get(uuid);
        if (last == null) {
            return false;
        }
        return (System.currentTimeMillis() - last) <= withinMillis;
    }

    public void remove(UUID uuid) {
        lastActivity.remove(uuid);
    }
}
