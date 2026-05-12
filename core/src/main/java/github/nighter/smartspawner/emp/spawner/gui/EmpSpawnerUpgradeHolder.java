package github.nighter.smartspawner.emp.spawner.gui;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class EmpSpawnerUpgradeHolder implements InventoryHolder {
    private final SpawnerData spawner;

    public EmpSpawnerUpgradeHolder(SpawnerData spawner) {
        this.spawner = spawner;
    }

    public SpawnerData getSpawner() {
        return spawner;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("EMP spawner upgrade holder does not provide direct inventory access");
    }
}
