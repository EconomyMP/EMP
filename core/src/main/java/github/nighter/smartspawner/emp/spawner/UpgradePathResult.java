package github.nighter.smartspawner.emp.spawner;

public record UpgradePathResult(boolean success, String errorKey, SpawnerUpgradeLevels before, SpawnerUpgradeLevels after,
                                String upgradedPath, long cost, String currency) {
    public static UpgradePathResult fail(String errorKey, SpawnerUpgradeLevels levels) {
        return new UpgradePathResult(false, errorKey, levels, levels, null, 0L, null);
    }

    public static UpgradePathResult ok(SpawnerUpgradeLevels before, SpawnerUpgradeLevels after,
                                       String path, long cost, String currency) {
        return new UpgradePathResult(true, null, before, after, path, cost, currency);
    }
}
