package github.nighter.smartspawner.emp.rank;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankService {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;
    private final List<RankDefinition> ranks = new ArrayList<>();

    public RankService(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
        reload();
    }

    public void reload() {
        ranks.clear();
        ConfigurationSection section = plugin.getEmpConfig().getConfig().getConfigurationSection("ranks.definitions");
        if (section == null) {
            ranks.add(new RankDefinition("NOVICE", "Novice", "Novice", 0L, "Just starting out"));
            ranks.add(new RankDefinition("VETERAN", "Veteran", "Veteran", 100000L, "Seasoned player"));
            ranks.add(new RankDefinition("ELITE", "Elite", "Elite", 1000000L, "Highly progressed"));
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection rankSection = section.getConfigurationSection(key);
            if (rankSection == null) {
                continue;
            }
            ranks.add(new RankDefinition(
                    key,
                    rankSection.getString("display_name", key),
                    rankSection.getString("prefix", key),
                    rankSection.getLong("threshold_balance", 0L),
                    rankSection.getString("description", "")
            ));
        }
        ranks.sort(Comparator.comparingLong(RankDefinition::thresholdBalance));
    }

    public RankDefinition getCurrentRank(UUID playerUuid) {
        long balance = accountService.getBalance(playerUuid, CurrencyType.MONEY);
        RankDefinition current = null;
        for (RankDefinition rank : ranks) {
            if (balance >= rank.thresholdBalance()) {
                current = rank;
            }
        }
        return current == null ? ranks.get(0) : current;
    }

    public RankDefinition getNextRank(UUID playerUuid) {
        long balance = accountService.getBalance(playerUuid, CurrencyType.MONEY);
        for (RankDefinition rank : ranks) {
            if (balance < rank.thresholdBalance()) {
                return rank;
            }
        }
        return null;
    }

    public List<RankDefinition> getRanks() {
        return List.copyOf(ranks);
    }

    public String buildChatTag(UUID playerUuid) {
        RankDefinition rank = getCurrentRank(playerUuid);
        return rank == null ? "" : rank.prefix();
    }

    public record RankDefinition(String key, String displayName, String prefix, long thresholdBalance, String description) {
    }
}
