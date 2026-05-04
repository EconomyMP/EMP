package github.nighter.smartspawner.emp.economy;

public record BalanceChangeResult(boolean success, long previousBalance, long newBalance, String error) {
    public static BalanceChangeResult success(long previousBalance, long newBalance) {
        return new BalanceChangeResult(true, previousBalance, newBalance, null);
    }

    public static BalanceChangeResult failure(String error, long previousBalance) {
        return new BalanceChangeResult(false, previousBalance, previousBalance, error);
    }
}
