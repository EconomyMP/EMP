package github.nighter.smartspawner.emp.economy;

import java.util.UUID;

public record BalanceEntry(UUID uuid, String name, long balance) {
}
