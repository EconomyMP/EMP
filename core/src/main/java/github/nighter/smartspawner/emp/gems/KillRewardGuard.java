package github.nighter.smartspawner.emp.gems;

import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class KillRewardGuard {
    private final ConcurrentHashMap<String, Long> lastKillByPair = new ConcurrentHashMap<>();

    public boolean canReward(Player killer, Player victim, long cooldownMillis, boolean blockSameIp) {
        if (killer == null || victim == null) {
            return false;
        }

        if (blockSameIp && sameIp(killer, victim)) {
            return false;
        }

        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = lastKillByPair.get(key);
        return last == null || (System.currentTimeMillis() - last) >= cooldownMillis;
    }

    public void registerKill(Player killer, Player victim) {
        if (killer == null || victim == null) {
            return;
        }
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        lastKillByPair.put(key, System.currentTimeMillis());
    }

    private boolean sameIp(Player killer, Player victim) {
        try {
            if (killer.getAddress() == null || victim.getAddress() == null) {
                return false;
            }
            InetAddress killerIp = killer.getAddress().getAddress();
            InetAddress victimIp = victim.getAddress().getAddress();
            return killerIp != null && killerIp.equals(victimIp);
        } catch (Exception e) {
            return false;
        }
    }
}
