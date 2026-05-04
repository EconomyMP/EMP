package github.nighter.smartspawner.emp.economy;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

public class VaultEconomyProvider extends AbstractEconomy {
    private final EmpAccountService accountService;
    private final CurrencyFormat moneyFormat;
    private final String singularName;
    private final String pluralName;

    public VaultEconomyProvider(EmpAccountService accountService, CurrencyFormat moneyFormat,
                                String singularName, String pluralName) {
        this.accountService = accountService;
        this.moneyFormat = moneyFormat;
        this.singularName = singularName == null ? "Coin" : singularName;
        this.pluralName = pluralName == null ? "Coins" : pluralName;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "EMP-Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return moneyFormat.getDecimals();
    }

    @Override
    public String format(double amount) {
        return moneyFormat.format(toMinorUnits(amount));
    }

    @Override
    public String currencyNamePlural() {
        return pluralName;
    }

    @Override
    public String currencyNameSingular() {
        return singularName;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        long balance = accountService.getBalance(player.getUniqueId(), CurrencyType.MONEY);
        return toMajorUnits(balance);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(String playerName, String worldName) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String worldName) {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        long balance = accountService.getBalance(player.getUniqueId(), CurrencyType.MONEY);
        return balance >= toMinorUnits(amount);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        long minor = toMinorUnits(amount);
        BalanceChangeResult result = accountService.takeBalance(player.getUniqueId(), player.getName(),
                CurrencyType.MONEY, minor, "VAULT_WITHDRAW", "Vault", null);

        if (!result.success()) {
            return new EconomyResponse(amount, toMajorUnits(result.previousBalance()),
                    EconomyResponse.ResponseType.FAILURE, result.error());
        }

        return new EconomyResponse(amount, toMajorUnits(result.newBalance()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        long minor = toMinorUnits(amount);
        BalanceChangeResult result = accountService.addBalance(player.getUniqueId(), player.getName(),
                CurrencyType.MONEY, minor, "VAULT_DEPOSIT", "Vault", null);

        if (!result.success()) {
            return new EconomyResponse(amount, toMajorUnits(result.previousBalance()),
                    EconomyResponse.ResponseType.FAILURE, result.error());
        }

        return new EconomyResponse(amount, toMajorUnits(result.newBalance()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return unsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        accountService.ensureAccount(player.getUniqueId(), player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private EconomyResponse unsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "banking_unsupported");
    }

    private long toMinorUnits(double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);
        value = value.setScale(moneyFormat.getDecimals(), RoundingMode.HALF_UP);
        return value.movePointRight(moneyFormat.getDecimals()).longValueExact();
    }

    private double toMajorUnits(long amount) {
        return BigDecimal.valueOf(amount, moneyFormat.getDecimals()).doubleValue();
    }
}
