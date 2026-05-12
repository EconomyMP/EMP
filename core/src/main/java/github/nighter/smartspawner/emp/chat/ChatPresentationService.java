package github.nighter.smartspawner.emp.chat;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.bounty.BountyService;
import github.nighter.smartspawner.emp.killstreak.KillstreakService;
import github.nighter.smartspawner.emp.rank.RankService;
import github.nighter.smartspawner.emp.team.TeamService;
import github.nighter.smartspawner.emp.team.TeamService.TeamMembership;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Optional;

public class ChatPresentationService {
    private final SmartSpawner plugin;
    private final TeamService teamService;
    private final BountyService bountyService;
    private final KillstreakService killstreakService;
    private final RankService rankService;

    public ChatPresentationService(SmartSpawner plugin, TeamService teamService, BountyService bountyService,
                                   KillstreakService killstreakService, RankService rankService) {
        this.plugin = plugin;
        this.teamService = teamService;
        this.bountyService = bountyService;
        this.killstreakService = killstreakService;
        this.rankService = rankService;
    }

    public void refresh(Player player) {
        if (player == null) {
            return;
        }

        String tabName = buildTabName(player);
        player.setPlayerListName(tabName);
        player.displayName(Component.text(tabName).color(NamedTextColor.WHITE));
    }

    public void refreshOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    public Component renderChat(Player sender, Component sourceDisplayName, Component message) {
        Optional<TeamMembership> membership = teamService.getMembership(sender.getUniqueId());
        int bounty = (int) Math.min(Integer.MAX_VALUE, bountyService.getBounty(sender.getUniqueId()));
        int streak = killstreakService.getCurrentStreak(sender.getUniqueId());

        Component prefix = Component.empty();
        if (membership.isPresent()) {
            String teamTag = shorten(membership.get().teamName(), 10);
            prefix = prefix.append(Component.text("[").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(teamTag).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                    .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));
        }

        if (rankService != null) {
            String rankTag = rankService.buildChatTag(sender.getUniqueId());
            if (!rankTag.isBlank()) {
                prefix = prefix.append(Component.text("{").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(shorten(rankTag, 8)).color(NamedTextColor.AQUA))
                        .append(Component.text("} ").color(NamedTextColor.DARK_GRAY));
            }
        }
        if (bounty > 0) {
            prefix = prefix.append(Component.text("$").color(NamedTextColor.GREEN))
                    .append(Component.text(String.valueOf(bounty)).color(NamedTextColor.GREEN))
                    .append(Component.space());
        }
        if (streak > 0) {
            prefix = prefix.append(Component.text("x").color(NamedTextColor.RED))
                    .append(Component.text(String.valueOf(streak)).color(NamedTextColor.RED))
                    .append(Component.space());
        }

        return Component.empty()
                .append(prefix)
                .append(sourceDisplayName)
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(message);
    }

    private String buildTabName(Player player) {
        String base = player.getName();
        if (rankService != null) {
            String rankTag = rankService.buildChatTag(player.getUniqueId());
            if (!rankTag.isBlank()) {
                base = "{" + shorten(rankTag, 8) + "} " + base;
            }
        }
        Optional<TeamMembership> membership = teamService.getMembership(player.getUniqueId());
        if (membership.isPresent()) {
            base = "[" + shorten(membership.get().teamName(), 8) + "] " + base;
        }
        int streak = killstreakService.getCurrentStreak(player.getUniqueId());
        if (streak > 0) {
            base = base + " x" + streak;
        }
        return shorten(base, 16);
    }

    private String shorten(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        if (input.length() <= maxLength) {
            return input;
        }
        return input.substring(0, maxLength);
    }
}
