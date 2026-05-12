package github.nighter.smartspawner.emp.spawner.gui;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.emp.spawner.EmpSpawnerProgressionService;
import github.nighter.smartspawner.emp.spawner.SpawnerTier;
import github.nighter.smartspawner.emp.spawner.SpawnerUpgradeLevels;
import github.nighter.smartspawner.emp.spawner.UpgradePathResult;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class EmpSpawnerGuiService implements Listener {
    private final SmartSpawner plugin;
    private final EmpSpawnerProgressionService progressionService;
    private final EmpAccountService accountService;

    public EmpSpawnerGuiService(SmartSpawner plugin, EmpSpawnerProgressionService progressionService,
                                EmpAccountService accountService) {
        this.plugin = plugin;
        this.progressionService = progressionService;
        this.accountService = accountService;
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(new EmpSpawnerShopHolder(), 54, "EMP Spawner Shop");

        List<SpawnerTier> tiers = new ArrayList<>(progressionService.getTiers());
        tiers.sort(Comparator.comparingLong(SpawnerTier::priceGems));

        int slot = 10;
        for (SpawnerTier tier : tiers) {
            if (slot >= 44) {
                break;
            }
            inv.setItem(slot, buildTierItem(tier));
            slot++;
            if (slot % 9 == 8) {
                slot += 2;
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    public void openUpgrade(Player player, SpawnerData spawner) {
        Inventory inv = Bukkit.createInventory(new EmpSpawnerUpgradeHolder(spawner), 27, "EMP Spawner Upgrades");

        SpawnerTier tier = progressionService.getTierByEntity(spawner.getEntityType());
        if (tier == null) {
            player.sendMessage("This spawner type has no EMP tier configuration.");
            return;
        }

        SpawnerUpgradeLevels levels = progressionService.getLevels(spawner.getSpawnerId());
        inv.setItem(11, buildUpgradeItem(Material.CLOCK, "Speed", levels.speedLevel(), tier.speedMax(), tier.speedCurrency(), nextCost(tier.speedCosts(), levels.speedLevel())));
        inv.setItem(13, buildUpgradeItem(Material.NETHER_STAR, "Multiplier", levels.multiplierLevel(), tier.multiplierMax(), tier.multiplierCurrency(), nextCost(tier.multiplierCosts(), levels.multiplierLevel())));
        inv.setItem(15, buildUpgradeItem(Material.BEACON, "Efficiency", levels.efficiencyLevel(), tier.efficiencyMax(), tier.efficiencyCurrency(), nextCost(tier.efficiencyCosts(), levels.efficiencyLevel())));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getInventory().getHolder(false) instanceof EmpSpawnerShopHolder) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) {
                return;
            }

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) {
                return;
            }

            String key = meta.getPersistentDataContainer().get(plugin.getNamespacedKey("emp_tier"), org.bukkit.persistence.PersistentDataType.STRING);
            if (key == null) {
                return;
            }

            handlePurchase(player, key);
            return;
        }

        if (event.getInventory().getHolder(false) instanceof EmpSpawnerUpgradeHolder holder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            String path;
            if (slot == 11) {
                path = "speed";
            } else if (slot == 13) {
                path = "multiplier";
            } else if (slot == 15) {
                path = "efficiency";
            } else {
                return;
            }

            SpawnerData spawner = holder.getSpawner();
            Scheduler.runTaskAsync(() -> {
                UpgradePathResult result = progressionService.upgrade(spawner, path, player.getUniqueId(), player.getName());
                Scheduler.runTask(() -> {
                    if (!result.success()) {
                        plugin.getMessageService().sendMessage(player, result.errorKey());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                        return;
                    }

                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("path", result.upgradedPath());
                    placeholders.put("cost", formatCurrency(result.currency(), result.cost()));
                    plugin.getMessageService().sendMessage(player, "emp.spawners.upgrade.success", placeholders);
                    openUpgrade(player, spawner);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                });
            });
        }
    }

    private void handlePurchase(Player player, String key) {
        SpawnerTier tier = progressionService.getTiers().stream()
                .filter(t -> t.key().equalsIgnoreCase(key))
                .findFirst().orElse(null);
        if (tier == null) {
            plugin.getMessageService().sendMessage(player, "emp.spawners.shop.invalid");
            return;
        }

        Scheduler.runTaskAsync(() -> {
            BalanceChangeResult payment = accountService.takeBalance(player.getUniqueId(), player.getName(), CurrencyType.GEMS,
                    tier.priceGems(), "SPAWNER_BUY", "SpawnerShop", tier.key());

            Scheduler.runTask(() -> {
                if (!payment.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.spawners.shop.insufficient_gems");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    return;
                }

                ItemStack item = plugin.getSpawnerItemFactory().createSmartSpawnerItem(tier.entityType(), 1);
                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                } else {
                    player.getInventory().addItem(item);
                }

                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("spawner", tier.displayName());
                placeholders.put("cost", accountService.getGemFormat().format(tier.priceGems()));
                plugin.getMessageService().sendMessage(player, "emp.spawners.shop.purchased", placeholders);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.1f);
            });
        });
    }

    private ItemStack buildTierItem(SpawnerTier tier) {
        ItemStack stack = new ItemStack(Material.SPAWNER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName("§a" + tier.displayName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Price: §b" + accountService.getGemFormat().format(tier.priceGems()));
        lore.add("§7Output: §f" + tier.outputPerMinute() + " §7" + prettify(tier.outputMaterial()) + " / minute");
        lore.add("§8Click to purchase with gems");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(plugin.getNamespacedKey("emp_tier"), org.bukkit.persistence.PersistentDataType.STRING, tier.key());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildUpgradeItem(Material material, String name, int level, int max, String currency, long nextCost) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        meta.setDisplayName("§b" + name + " Upgrade");
        List<String> lore = new ArrayList<>();
        lore.add("§7Level: §f" + level + "§7/" + max);
        if (level >= max) {
            lore.add("§aMaxed");
        } else {
            lore.add("§7Next cost: §f" + formatCurrency(currency, nextCost));
            lore.add("§8Click to upgrade");
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private long nextCost(long[] costs, int currentLevel) {
        if (costs.length == 0) {
            return 0L;
        }
        int idx = Math.min(costs.length - 1, currentLevel);
        return costs[idx];
    }

    private String formatCurrency(String currency, long amount) {
        if ("GEMS".equalsIgnoreCase(currency)) {
            return accountService.getGemFormat().format(amount);
        }
        return accountService.getMoneyFormat().format(amount);
    }

    private String prettify(Material material) {
        String lower = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return out.toString().trim();
    }
}
