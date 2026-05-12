package github.nighter.smartspawner.emp.spawner.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class EmpSpawnerShopHolder implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("EMP spawner shop holder does not provide direct inventory access");
    }
}
