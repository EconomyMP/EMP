package github.nighter.smartspawner.emp.spawner;

public record SpawnerUpgradeLevels(int speedLevel, int multiplierLevel, int efficiencyLevel) {
    public static final SpawnerUpgradeLevels ZERO = new SpawnerUpgradeLevels(0, 0, 0);

    public SpawnerUpgradeLevels withSpeed(int level) {
        return new SpawnerUpgradeLevels(level, multiplierLevel, efficiencyLevel);
    }

    public SpawnerUpgradeLevels withMultiplier(int level) {
        return new SpawnerUpgradeLevels(speedLevel, level, efficiencyLevel);
    }

    public SpawnerUpgradeLevels withEfficiency(int level) {
        return new SpawnerUpgradeLevels(speedLevel, multiplierLevel, level);
    }
}
