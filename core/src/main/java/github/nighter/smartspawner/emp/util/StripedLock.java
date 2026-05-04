package github.nighter.smartspawner.emp.util;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class StripedLock {
    private final ReentrantLock[] locks;

    public StripedLock(int stripes) {
        if (stripes <= 0) {
            throw new IllegalArgumentException("stripes must be positive");
        }
        this.locks = new ReentrantLock[stripes];
        for (int i = 0; i < stripes; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public ReentrantLock lockFor(UUID uuid) {
        int idx = Math.abs(uuid.hashCode() % locks.length);
        return locks[idx];
    }
}
