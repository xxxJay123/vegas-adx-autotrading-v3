package com.vegas.backtest.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration loader from .env file.
 * Singleton pattern for global access to configuration.
 */
@Getter
public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static Config instance;

    private final Dotenv dotenv;

    private final String dataDir;
    private final String reportsDir;

    private final int ema12Len;
    private final int ema144Len;
    private final int ema169Len;
    private final int ema576Len;
    private final int ema676Len;
    private final int adxPeriod;
    private final double adxThreshold;
    private final double adxMaxThreshold;
    private final boolean adxRequireSlopeUp;

    private final int stopLookback;
    private final int leverage;
    private final double fixedNotionalUsdt;
    private final double rewardRatio;
    private final double minRewardRatio;

    private final double makerFeePercent;
    private final double takerFeePercent;

    private final boolean[] longRulesEnabled;
    private final boolean[] shortRulesEnabled;

    private final int pattern2bLookback;
    private final int patternDoubleLookback;

    // Volume filter configuration
    private final int volumeAvgPeriod;
    private final double volumeSpikeRatio;

    // Data source configuration
    private final String dataSource; // "csv" or "binance"
    private final List<String> timeframes; // e.g., ["5m", "15m", "1h"]

    // Binance API configuration
    private final String binanceApiKey;
    private final String binanceSecretKey;
    private final String binanceSymbol;
    private final String binanceStartDate;
    private final String binanceEndDate;

    // 時間過濾設置
    private final Set<Integer> blockedHours;
    private final Set<Integer> blockedDays;

    private final boolean autoOpenReports;

    // Market Regime Detection
    private final boolean enableMarketRegimeFilter;
    private final boolean pauseOnHighVolatilityRange;
    private final double adxStrongTrendThreshold;
    private final double adxModerateTrendThreshold;

    // Dynamic Reward Ratio
    private final boolean enableDynamicRewardRatio;
    private final double strongTrendRewardRatio;
    private final double moderateTrendRewardRatio;
    private final double rangingRewardRatio;

    // Fixed Risk Position Sizing
    private final boolean enableFixedRiskSizing;
    private final double fixedRiskPerTradeUsdt;

    // Max Holding Time
    private final boolean enableMaxHoldingTime;
    private final int maxHoldingTimeHours;

    // Dynamic Leverage
    private final boolean enableDynamicLeverage;
    private final int baseLeverage;
    private final double strongTrendLeverageMultiplier;
    private final double moderateTrendLeverageMultiplier;
    private final double lowVolRangeLeverageMultiplier;
    private final double highVolRangeLeverageMultiplier;
    private final int minLeverage;
    private final int maxLeverage;

    private Config() {
        this.dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        this.dataDir = getEnv("DATA_DIR", "./data/kline");
        this.reportsDir = getEnv("REPORTS_DIR", "./data/reports");

        this.ema12Len = getEnvInt("EMA12_LEN", 12);
        this.ema144Len = getEnvInt("EMA144_LEN", 144);
        this.ema169Len = getEnvInt("EMA169_LEN", 169);
        this.ema576Len = getEnvInt("EMA576_LEN", 576);
        this.ema676Len = getEnvInt("EMA676_LEN", 676);
        this.adxPeriod = getEnvInt("ADX_PERIOD", 14);
        this.adxThreshold = getEnvDouble("ADX_THRESHOLD", 30.0);
        this.adxMaxThreshold = getEnvDouble("ADX_MAX_THRESHOLD", 100.0);
        this.adxRequireSlopeUp = getEnvBoolean("ADX_REQUIRE_SLOPE_UP", false);

        this.stopLookback = getEnvInt("STOP_LOOKBACK", 136);
        this.leverage = getEnvInt("LEVERAGE", 50);
        this.fixedNotionalUsdt = getEnvDouble("FIXED_NOTIONAL_USDT", 100.0);
        this.rewardRatio = getEnvDouble("REWARD_RATIO", 3.7);
        this.minRewardRatio = getEnvDouble("MIN_REWARD_RATIO", 2.0);

        this.makerFeePercent = getEnvDouble("MAKER_FEE_PERCENT", 0.02);
        this.takerFeePercent = getEnvDouble("TAKER_FEE_PERCENT", 0.075);

        this.longRulesEnabled = new boolean[8];
        for (int i = 0; i < 8; i++) {
            this.longRulesEnabled[i] = getEnvBoolean("LONG_RULE_" + (i + 1) + "_ENABLE", true);
        }

        this.shortRulesEnabled = new boolean[8];
        for (int i = 0; i < 8; i++) {
            this.shortRulesEnabled[i] = getEnvBoolean("SHORT_RULE_" + (i + 1) + "_ENABLE", true);
        }
        // Data source configuration
        this.dataSource = getEnv("DATA_SOURCE", "csv").toLowerCase();
        this.timeframes = parseTimeframes(getEnv("TIMEFRAMES", "5m"));

        // Binance API configuration
        this.binanceApiKey = getEnv("BINANCE_API_KEY", "");
        this.binanceSecretKey = getEnv("BINANCE_SECRET_KEY", "");
        this.binanceSymbol = getEnv("BINANCE_SYMBOL", "BTCUSDT");
        this.binanceStartDate = getEnv("BINANCE_START_DATE", "");
        this.binanceEndDate = getEnv("BINANCE_END_DATE", "");
        this.pattern2bLookback = getEnvInt("PATTERN_2B_LOOKBACK", 10);
        this.patternDoubleLookback = getEnvInt("PATTERN_DOUBLE_LOOKBACK", 20);
        this.volumeAvgPeriod = getEnvInt("VOLUME_AVG_PERIOD", 20);
        this.volumeSpikeRatio = getEnvDouble("VOLUME_SPIKE_RATIO", 3.0);

        // 時間過濾設置
        this.blockedHours = parseIntSet(getEnv("BLOCKED_HOURS", ""));
        this.blockedDays = parseIntSet(getEnv("BLOCKED_DAYS", ""));

        this.autoOpenReports = getEnvBoolean("AUTO_OPEN_REPORTS", true);

        // Market Regime Detection
        this.enableMarketRegimeFilter = getEnvBoolean("ENABLE_MARKET_REGIME_FILTER", false);
        this.pauseOnHighVolatilityRange = getEnvBoolean("PAUSE_ON_HIGH_VOLATILITY_RANGE", true);
        this.adxStrongTrendThreshold = getEnvDouble("ADX_STRONG_TREND_THRESHOLD", 40.0);
        this.adxModerateTrendThreshold = getEnvDouble("ADX_MODERATE_TREND_THRESHOLD", 25.0);

        // Dynamic Reward Ratio
        this.enableDynamicRewardRatio = getEnvBoolean("ENABLE_DYNAMIC_REWARD_RATIO", false);
        this.strongTrendRewardRatio = getEnvDouble("STRONG_TREND_REWARD_RATIO", 3.7);
        this.moderateTrendRewardRatio = getEnvDouble("MODERATE_TREND_REWARD_RATIO", 2.5);
        this.rangingRewardRatio = getEnvDouble("RANGING_REWARD_RATIO", 1.8);

        // Fixed Risk Position Sizing
        this.enableFixedRiskSizing = getEnvBoolean("ENABLE_FIXED_RISK_SIZING", false);
        this.fixedRiskPerTradeUsdt = getEnvDouble("FIXED_RISK_PER_TRADE_USDT", 100.0);

        // Max Holding Time
        this.enableMaxHoldingTime = getEnvBoolean("ENABLE_MAX_HOLDING_TIME", false);
        this.maxHoldingTimeHours = getEnvInt("MAX_HOLDING_TIME_HOURS", 336); // 2 weeks default

        // Dynamic Leverage
        this.enableDynamicLeverage = getEnvBoolean("ENABLE_DYNAMIC_LEVERAGE", false);
        this.baseLeverage = getEnvInt("BASE_LEVERAGE", 50);
        this.strongTrendLeverageMultiplier = getEnvDouble("STRONG_TREND_LEVERAGE_MULTIPLIER", 1.0);
        this.moderateTrendLeverageMultiplier = getEnvDouble("MODERATE_TREND_LEVERAGE_MULTIPLIER", 0.7);
        this.lowVolRangeLeverageMultiplier = getEnvDouble("LOW_VOL_RANGE_LEVERAGE_MULTIPLIER", 0.4);
        this.highVolRangeLeverageMultiplier = getEnvDouble("HIGH_VOL_RANGE_LEVERAGE_MULTIPLIER", 0.2);
        this.minLeverage = getEnvInt("MIN_LEVERAGE", 5);
        this.maxLeverage = getEnvInt("MAX_LEVERAGE", 100);

        log.info("Configuration loaded successfully");
        log.info("Data Source: {}", dataSource);
        log.info("Timeframes: {}", timeframes);
        logConfiguration();

        if ("binance".equals(dataSource)) {
            log.info("Binance Symbol: {}", binanceSymbol);
            log.info("Binance Date Range: {} to {}", binanceStartDate, binanceEndDate);
            log.info("Binance API Key: {}", binanceApiKey.isEmpty() ? "Not set" : "***");
        }
    }

    /**
     * Parse comma-separated timeframes into a List
     * 
     * @param value comma-separated timeframes (e.g., "5m,15m,1h")
     * @return list of timeframe strings
     */
    private List<String> parseTimeframes(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Arrays.asList("5m"); // default
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Get singleton instance
     * 
     * @return Config instance
     */
    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    /**
     * Get string from env with default
     */
    private String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get int from env with default
     */
    private int getEnvInt(String key, int defaultValue) {
        String value = dotenv.get(key);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get double from env with default
     */
    private double getEnvDouble(String key, double defaultValue) {
        String value = dotenv.get(key);
        if (value == null)
            return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double for {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get boolean from env with default
     */
    private boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = dotenv.get(key);
        if (value == null)
            return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * Parse comma-separated integers into a Set
     */
    private Set<Integer> parseIntSet(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid integer in list: {}", s);
                        return null;
                    }
                })
                .filter(i -> i != null)
                .collect(Collectors.toSet());
    }

    /**
     * Check if a long rule is enabled
     * 
     * @param ruleNumber rule number (1-8)
     * @return true if enabled
     */
    public boolean isLongRuleEnabled(int ruleNumber) {
        if (ruleNumber < 1 || ruleNumber > 8)
            return false;
        return longRulesEnabled[ruleNumber - 1];
    }

    /**
     * Check if a short rule is enabled
     * 
     * @param ruleNumber rule number (1-8)
     * @return true if enabled
     */
    public boolean isShortRuleEnabled(int ruleNumber) {
        if (ruleNumber < 1 || ruleNumber > 8)
            return false;
        return shortRulesEnabled[ruleNumber - 1];
    }

    /**
     * Check if a specific hour is blocked for trading
     * 
     * @param hour hour (0-23 UTC)
     * @return true if blocked
     */
    public boolean isHourBlocked(int hour) {
        return blockedHours.contains(hour);
    }

    /**
     * Check if a specific day is blocked for trading
     * 
     * @param dayOfWeek day of week (1=Monday, 7=Sunday)
     * @return true if blocked
     */
    public boolean isDayBlocked(int dayOfWeek) {
        return blockedDays.contains(dayOfWeek);
    }

    /**
     * Log current configuration
     */
    private void logConfiguration() {
        log.info("=== BACKTESTING CONFIGURATION ===");
        log.info("Data Directory: {}", dataDir);
        log.info("Reports Directory: {}", reportsDir);
        log.info("EMA Lengths: 12={}, 144={}, 169={}, 576={}, 676={}",
                ema12Len, ema144Len, ema169Len, ema576Len, ema676Len);
        log.info("ADX: period={}, threshold={}", adxPeriod, adxThreshold);
        log.info("Position Sizing: notional={}USDT, leverage={}x", fixedNotionalUsdt, leverage);
        log.info("Risk/Reward: ratio={}, min={}", rewardRatio, minRewardRatio);
        log.info("Fees: maker={}%, taker={}%", makerFeePercent, takerFeePercent);

        StringBuilder longRules = new StringBuilder("Long Rules: ");
        for (int i = 0; i < 8; i++) {
            longRules.append("R").append(i + 1).append("=").append(longRulesEnabled[i] ? "ON" : "OFF").append(" ");
        }
        log.info(longRules.toString());

        StringBuilder shortRules = new StringBuilder("Short Rules: ");
        for (int i = 0; i < 8; i++) {
            shortRules.append("R").append(i + 1).append("=").append(shortRulesEnabled[i] ? "ON" : "OFF").append(" ");
        }
        log.info(shortRules.toString());

        // 時間過濾設置
        if (!blockedHours.isEmpty()) {
            log.info("Blocked Hours (UTC): {}", blockedHours);
        }
        if (!blockedDays.isEmpty()) {
            log.info("Blocked Days: {} (1=Mon, 7=Sun)", blockedDays);
        }
        log.info("================================");
    }
}
