package github.nighter.smartspawner.emp.spawner;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record SpawnerTier(
        String key,
        String displayName,
        EntityType entityType,
        Material outputMaterial,
        int outputPerMinute,
        long priceGems,
        int speedMax,
        double speedDelayReductionPerLevel,
        int multiplierMax,
        double multiplierPerLevel,
        int efficiencyMax,
        double efficiencyChanceBonusPerLevel,
        String speedCurrency,
        String multiplierCurrency,
        String efficiencyCurrency,
        long[] speedCosts,
        long[] multiplierCosts,
        long[] efficiencyCosts
) {
}
