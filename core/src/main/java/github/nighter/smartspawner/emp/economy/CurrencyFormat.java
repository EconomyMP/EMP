package github.nighter.smartspawner.emp.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CurrencyFormat {
    private final String symbol;
    private final String format;
    private final int decimals;
    private final char thousandsSeparator;
    private final char decimalSeparator;
    private final long scale;

    public CurrencyFormat(String symbol, String format, int decimals, char thousandsSeparator, char decimalSeparator) {
        this.symbol = symbol == null ? "" : symbol;
        this.format = format == null ? "{symbol}{amount}" : format;
        this.decimals = Math.max(0, decimals);
        this.thousandsSeparator = thousandsSeparator;
        this.decimalSeparator = decimalSeparator;
        this.scale = (long) Math.pow(10, this.decimals);
    }

    public String format(long amount) {
        BigDecimal value = BigDecimal.valueOf(amount, decimals);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(thousandsSeparator);
        symbols.setDecimalSeparator(decimalSeparator);

        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(symbols);
        df.setGroupingUsed(true);
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);

        String formatted = df.format(value);
        return format.replace("{symbol}", symbol).replace("{amount}", formatted);
    }

    public long parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        String cleaned = input.trim();
        if (!symbol.isEmpty()) {
            cleaned = cleaned.replace(symbol, "");
        }
        cleaned = cleaned.replace(String.valueOf(thousandsSeparator), "");
        if (decimalSeparator != '.') {
            cleaned = cleaned.replace(decimalSeparator, '.');
        }

        BigDecimal value = new BigDecimal(cleaned);
        value = value.setScale(decimals, RoundingMode.HALF_UP);
        return value.movePointRight(decimals).longValueExact();
    }

    public int getDecimals() {
        return decimals;
    }

    public long getScale() {
        return scale;
    }

    public String getSymbol() {
        return symbol;
    }
}
