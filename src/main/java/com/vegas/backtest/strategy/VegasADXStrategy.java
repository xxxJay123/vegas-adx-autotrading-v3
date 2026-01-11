package com.vegas.backtest.strategy;

import com.vegas.backtest.config.Config;
import com.vegas.backtest.indicator.ADX;
import com.vegas.backtest.indicator.EMA;
import com.vegas.backtest.indicator.MarketRegime;
import com.vegas.backtest.model.Candle;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Vegas ADX Strategy with 16 independent rules (8 long + 8 short).
 * IMPROVED VERSION with trend confirmation and additional filters.
 */
public class VegasADXStrategy {

    private static final Logger log = LoggerFactory.getLogger(VegasADXStrategy.class);

    private final Config config;

    private final EMA ema12;
    private final EMA ema144;
    private final EMA ema169;
    private final EMA ema576;
    private final EMA ema676;
    private final ADX adx;
    private final MarketRegime marketRegime;

    private final Deque<Candle> priceHistory;
    private final int maxHistorySize;

    @Getter
    private Candle previousCandle;

    @Getter
    private Candle currentCandle;

    private boolean wasAboveEma12;
    private boolean wasBelowEma12;
    private long lastTouchMinLongEmas;
    private long lastTouchMaxShortEmas;
    private long lastTouchMinMidEmas;
    private long lastTouchMaxMidEmas;
    private int crossCountAfterTouchLong;
    private int crossCountAfterTouchShort;
    private int crossCountAfterMidTouchLong;
    private int crossCountAfterMidTouchShort;

    /**
     * Create strategy with configuration
     * @param config configuration
     */
    public VegasADXStrategy(Config config) {
        this.config = config;

        this.ema12 = new EMA(config.getEma12Len());
        this.ema144 = new EMA(config.getEma144Len());
        this.ema169 = new EMA(config.getEma169Len());
        this.ema576 = new EMA(config.getEma576Len());
        this.ema676 = new EMA(config.getEma676Len());
        this.adx = new ADX(config.getAdxPeriod());

        // Initialize MarketRegime if enabled
        if (config.isEnableMarketRegimeFilter()) {
            this.marketRegime = new MarketRegime(
                config.getAdxStrongTrendThreshold(),
                config.getAdxModerateTrendThreshold()
            );
        } else {
            this.marketRegime = null;
        }

        this.maxHistorySize = Math.max(
            config.getStopLookback(),
            Math.max(config.getPattern2bLookback(), config.getPatternDoubleLookback())
        ) + 50;

        this.priceHistory = new ArrayDeque<>(maxHistorySize);

        this.wasAboveEma12 = false;
        this.wasBelowEma12 = false;
        this.lastTouchMinLongEmas = 0;
        this.lastTouchMaxShortEmas = 0;
        this.lastTouchMinMidEmas = 0;
        this.lastTouchMaxMidEmas = 0;
        this.crossCountAfterTouchLong = 0;
        this.crossCountAfterTouchShort = 0;
        this.crossCountAfterMidTouchLong = 0;
        this.crossCountAfterMidTouchShort = 0;
    }

    /**
     * Update strategy with new candle
     * @param candle new candle
     */
    public void update(Candle candle) {
        this.previousCandle = this.currentCandle;
        this.currentCandle = candle;

        ema12.update(candle.getClose());
        ema144.update(candle.getClose());
        ema169.update(candle.getClose());
        ema576.update(candle.getClose());
        ema676.update(candle.getClose());
        adx.update(candle);

        // Update market regime if enabled
        if (marketRegime != null) {
            marketRegime.update(candle, adx.getValue());
        }

        priceHistory.addLast(candle);
        if (priceHistory.size() > maxHistorySize) {
            priceHistory.removeFirst();
        }

        updateTrendTracking();
    }

    /**
     * Update trend tracking variables
     */
    private void updateTrendTracking() {
        if (!isReady()) return;

        double close = currentCandle.getClose();
        double low = currentCandle.getLow();
        double high = currentCandle.getHigh();

        double minLongEmas = Math.min(ema576.getValue(), ema676.getValue());
        double maxShortEmas = Math.max(ema576.getValue(), ema676.getValue());
        double minMidEmas = Math.min(ema144.getValue(), ema169.getValue());
        double maxMidEmas = Math.max(ema144.getValue(), ema169.getValue());

        boolean touchedMinLongEmas = low <= minLongEmas;
        boolean touchedMaxShortEmas = high >= maxShortEmas;
        boolean touchedMinMidEmas = low <= minMidEmas;
        boolean touchedMaxMidEmas = high >= maxMidEmas;

        if (touchedMinLongEmas) {
            lastTouchMinLongEmas = currentCandle.getTimestamp();
            crossCountAfterTouchLong = 0;
        }

        if (touchedMaxShortEmas) {
            lastTouchMaxShortEmas = currentCandle.getTimestamp();
            crossCountAfterTouchShort = 0;
        }

        if (touchedMinMidEmas) {
            lastTouchMinMidEmas = currentCandle.getTimestamp();
            crossCountAfterMidTouchLong = 0;
        }

        if (touchedMaxMidEmas) {
            lastTouchMaxMidEmas = currentCandle.getTimestamp();
            crossCountAfterMidTouchShort = 0;
        }

        if (previousCandle != null) {
            boolean prevAbove = previousCandle.getClose() > ema12.getValue();
            boolean currAbove = close > ema12.getValue();

            if (!prevAbove && currAbove) {
                if (lastTouchMinLongEmas > 0) crossCountAfterTouchLong++;
                if (lastTouchMinMidEmas > 0) crossCountAfterMidTouchLong++;
            }

            if (prevAbove && !currAbove) {
                if (lastTouchMaxShortEmas > 0) crossCountAfterTouchShort++;
                if (lastTouchMaxMidEmas > 0) crossCountAfterMidTouchShort++;
            }

            wasAboveEma12 = prevAbove;
            wasBelowEma12 = !prevAbove;
        }
    }

    /**
     * Check if strategy is ready (all indicators initialized)
     * @return true if ready
     */
    public boolean isReady() {
        return ema12.isReady() && ema144.isReady() && ema169.isReady()
            && ema576.isReady() && ema676.isReady() && adx.isReady();
    }

    /**
     * Check for long entry signal
     * @return rule number (1-8) if signal found, 0 otherwise
     */
    public int checkLongEntry() {
        if (!isReady()) {
            return 0;
        }

        // Common filters for all entries
        if (!passesCommonFilters()) {
            return 0;
        }

        if (config.isLongRuleEnabled(1) && checkLongRule1()) return 1;
        if (config.isLongRuleEnabled(2) && checkLongRule2()) return 2;
        if (config.isLongRuleEnabled(3) && checkLongRule3()) return 3;
        if (config.isLongRuleEnabled(4) && checkLongRule4()) return 4;
        if (config.isLongRuleEnabled(5) && checkLongRule5()) return 5;
        if (config.isLongRuleEnabled(6) && checkLongRule6()) return 6;
        if (config.isLongRuleEnabled(7) && checkLongRule7()) return 7;
        if (config.isLongRuleEnabled(8) && checkLongRule8()) return 8;

        return 0;
    }

    /**
     * Check for short entry signal
     * @return rule number (1-8) if signal found, 0 otherwise
     */
    public int checkShortEntry() {
        if (!isReady()) {
            return 0;
        }

        // Common filters for all entries
        if (!passesCommonFilters()) {
            return 0;
        }

        if (config.isShortRuleEnabled(1) && checkShortRule1()) return 1;
        if (config.isShortRuleEnabled(2) && checkShortRule2()) return 2;
        if (config.isShortRuleEnabled(3) && checkShortRule3()) return 3;
        if (config.isShortRuleEnabled(4) && checkShortRule4()) return 4;
        if (config.isShortRuleEnabled(5) && checkShortRule5()) return 5;
        if (config.isShortRuleEnabled(6) && checkShortRule6()) return 6;
        if (config.isShortRuleEnabled(7) && checkShortRule7()) return 7;
        if (config.isShortRuleEnabled(8) && checkShortRule8()) return 8;

        return 0;
    }

    /**
     * Common filters that apply to both long and short entries
     * @return true if all filters pass
     */
    private boolean passesCommonFilters() {
        // ADX must be above minimum threshold (trend exists)
        if (adx.getValue() < config.getAdxThreshold()) {
            return false;
        }

        // ADX must be below maximum threshold (trend not exhausted)
        if (adx.getValue() > config.getAdxMaxThreshold()) {
            return false;
        }

        // If configured, require ADX slope to be positive (trend strengthening)
        if (config.isAdxRequireSlopeUp() && !adx.isSlopeUp()) {
            return false;
        }

        // Market Regime Filter - pause trading in high volatility ranging markets
        if (config.isEnableMarketRegimeFilter() && marketRegime != null) {
            if (marketRegime.isReady() && config.isPauseOnHighVolatilityRange()) {
                if (marketRegime.shouldPauseTrading()) {
                    return false;
                }
            }
        }

        // Volume spike filter - avoid entering during abnormal volume
        if (isVolumeSpike()) {
            return false;
        }

        // Skip low-quality trading hours
        if (!isActiveTradingHour()) {
            return false;
        }

        return true;
    }

    /**
     * Check if current volume is a spike (abnormally high)
     * @return true if volume > avgVolume * spikeRatio
     */
    private boolean isVolumeSpike() {
        double avgVolume = getAverageVolume(config.getVolumeAvgPeriod());
        if (avgVolume <= 0) return false;

        double currentVolume = currentCandle.getVolume();
        return currentVolume > avgVolume * config.getVolumeSpikeRatio();
    }

    // ==================== LONG RULES (With Extra Filters) ====================

    /**
     * Long Rule 1: 1st cross above EMA12 after touching min(EMA576,EMA676)
     * + Trend confirmation + Momentum filter
     */
    private boolean checkLongRule1() {
        return lastTouchMinLongEmas > 0
            && crossCountAfterTouchLong == 1
            && isBullishCrossEma12()
            && hasBullishStructure()
            && hasMomentumConfirmation();
    }

    /**
     * Long Rule 2: 2nd cross above EMA12 after touching min(EMA576,EMA676) again
     * + Price must be recovering (close > EMA144)
     */
    private boolean checkLongRule2() {
        return lastTouchMinLongEmas > 0
            && crossCountAfterTouchLong == 2
            && isBullishCrossEma12()
            && currentCandle.getClose() > ema144.getValue();
    }

    /**
     * Long Rule 3: 1st cross + 2B pattern after touching min(EMA144,EMA169)
     * + Trend confirmation
     */
    private boolean checkLongRule3() {
        return lastTouchMinMidEmas > 0
            && crossCountAfterMidTouchLong == 1
            && isBullishCrossEma12()
            && detect2BPatternBullish()
            && hasBullishStructure();
    }

    /**
     * Long Rule 4: 2nd cross after touching min(EMA144,EMA169) (no 2B needed)
     * + Trend confirmation
     */
    private boolean checkLongRule4() {
        return lastTouchMinMidEmas > 0
            && crossCountAfterMidTouchLong == 2
            && isBullishCrossEma12()
            && hasBullishStructure();
    }

    /**
     * Long Rule 5: Trend continuation: was above EMA12 → pullback down → 2nd cross up
     * + Must be in established uptrend
     */
    private boolean checkLongRule5() {
        return wasAboveEma12
            && detectPullbackDown()
            && isBullishCrossEma12()
            && isBullTrend();
    }

    /**
     * Long Rule 6: Bear trend double bottom → 3rd fake break + 2B + cross EMA12
     */
    private boolean checkLongRule6() {
        return isBearTrend()
            && detectDoubleBottom()
            && detect2BPatternBullish()
            && isBullishCrossEma12();
    }

    /**
     * Long Rule 7: EMA144 already golden cross EMA169 → pullback → cross EMA12
     * + Price must be above mid EMAs
     */
    private boolean checkLongRule7() {
        return isGoldenCross()
            && detectPullbackDown()
            && isBullishCrossEma12()
            && currentCandle.getClose() > ema144.getValue();
    }

    /**
     * Long Rule 8: Bear trend, EMA12 in middle → touch mid zone → 2nd cross up
     */
    private boolean checkLongRule8() {
        return isBearTrend()
            && isEma12InMiddleZone()
            && lastTouchMinMidEmas > 0
            && crossCountAfterMidTouchLong >= 2
            && isBullishCrossEma12();
    }

    // ==================== SHORT RULES (Perfect Mirror with Extra Filters) ====================

    /**
     * Short Rule 1: 1st cross below EMA12 after touching max(EMA576,EMA676)
     * + Trend confirmation + Momentum filter
     */
    private boolean checkShortRule1() {
        return lastTouchMaxShortEmas > 0
            && crossCountAfterTouchShort == 1
            && isBearishCrossEma12()
            && hasBearishStructure()
            && hasMomentumConfirmationShort();
    }

    /**
     * Short Rule 2: 2nd cross below EMA12 after touching max(EMA576,EMA676) again
     * + Price must be falling (close < EMA144)
     */
    private boolean checkShortRule2() {
        return lastTouchMaxShortEmas > 0
            && crossCountAfterTouchShort == 2
            && isBearishCrossEma12()
            && currentCandle.getClose() < ema144.getValue();
    }

    /**
     * Short Rule 3: 1st cross + 2B pattern after touching max(EMA144,EMA169)
     * + Trend confirmation
     */
    private boolean checkShortRule3() {
        return lastTouchMaxMidEmas > 0
            && crossCountAfterMidTouchShort == 1
            && isBearishCrossEma12()
            && detect2BPatternBearish()
            && hasBearishStructure();
    }

    /**
     * Short Rule 4: 2nd cross after touching max(EMA144,EMA169) (no 2B needed)
     * + Trend confirmation
     */
    private boolean checkShortRule4() {
        return lastTouchMaxMidEmas > 0
            && crossCountAfterMidTouchShort == 2
            && isBearishCrossEma12()
            && hasBearishStructure();
    }

    /**
     * Short Rule 5: Trend continuation: was below EMA12 → pullback up → 2nd cross down
     * + Must be in established downtrend
     */
    private boolean checkShortRule5() {
        return wasBelowEma12
            && detectPullbackUp()
            && isBearishCrossEma12()
            && isBearTrend();
    }

    /**
     * Short Rule 6: Bull trend double top → 3rd fake break + 2B + cross EMA12
     */
    private boolean checkShortRule6() {
        return isBullTrend()
            && detectDoubleTop()
            && detect2BPatternBearish()
            && isBearishCrossEma12();
    }

    /**
     * Short Rule 7: EMA144 already death cross EMA169 → pullback up → cross EMA12
     * + Price must be below mid EMAs
     */
    private boolean checkShortRule7() {
        return isDeathCross()
            && detectPullbackUp()
            && isBearishCrossEma12()
            && currentCandle.getClose() < ema144.getValue();
    }

    /**
     * Short Rule 8: Bull trend, EMA12 in middle → touch mid zone → 2nd cross down
     */
    private boolean checkShortRule8() {
        return isBullTrend()
            && isEma12InMiddleZone()
            && lastTouchMaxMidEmas > 0
            && crossCountAfterMidTouchShort >= 2
            && isBearishCrossEma12();
    }

    // ==================== EXTRA FILTER HELPER METHODS ====================

    /**
     * Check for bullish EMA structure (EMA12 > EMA144 or close > EMA144)
     */
    private boolean hasBullishStructure() {
        return ema12.getValue() > ema144.getValue()
            || currentCandle.getClose() > ema144.getValue();
    }

    /**
     * Check for bearish EMA structure (EMA12 < EMA144 or close < EMA144)
     */
    private boolean hasBearishStructure() {
        return ema12.getValue() < ema144.getValue()
            || currentCandle.getClose() < ema144.getValue();
    }

    /**
     * Check momentum confirmation for longs
     * Bullish candle OR close above prior high
     */
    private boolean hasMomentumConfirmation() {
        if (previousCandle == null) return true;
        return currentCandle.getClose() > currentCandle.getOpen()
            || currentCandle.getClose() > previousCandle.getHigh();
    }

    /**
     * Check momentum confirmation for shorts
     * Bearish candle OR close below prior low
     */
    private boolean hasMomentumConfirmationShort() {
        if (previousCandle == null) return true;
        return currentCandle.getClose() < currentCandle.getOpen()
            || currentCandle.getClose() < previousCandle.getLow();
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if current time is allowed for trading
     * Uses configurable blocked hours and days from .env
     */
    private boolean isActiveTradingHour() {
        java.time.ZonedDateTime zdt = Instant.ofEpochMilli(currentCandle.getTimestamp())
            .atZone(ZoneId.of("UTC"));

        int hour = zdt.getHour();
        int dayOfWeek = zdt.getDayOfWeek().getValue();  // 1=Monday, 7=Sunday

        // Check if hour is blocked
        if (config.isHourBlocked(hour)) {
            return false;
        }

        // Check if day is blocked
        if (config.isDayBlocked(dayOfWeek)) {
            return false;
        }

        // Default: Active hours 06:00 - 22:00 UTC
        return hour >= 6 && hour <= 22;
    }

    // ==================== EXISTING HELPER METHODS ====================

    /**
     * Check if current candle is bullish cross above EMA12
     */
    private boolean isBullishCrossEma12() {
        if (previousCandle == null) return false;
        return previousCandle.getClose() <= ema12.getValue()
            && currentCandle.getClose() > ema12.getValue();
    }

    /**
     * Check if current candle is bearish cross below EMA12
     */
    private boolean isBearishCrossEma12() {
        if (previousCandle == null) return false;
        return previousCandle.getClose() >= ema12.getValue()
            && currentCandle.getClose() < ema12.getValue();
    }

    /**
     * Detect 2B bullish pattern (lower low followed by higher high)
     */
    private boolean detect2BPatternBullish() {
        if (priceHistory.size() < config.getPattern2bLookback()) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - config.getPattern2bLookback()))
            .toArray(Candle[]::new);

        if (recent.length < 3) return false;

        double lowestLow = Double.MAX_VALUE;
        for (int i = 0; i < recent.length - 1; i++) {
            if (recent[i].getLow() < lowestLow) {
                lowestLow = recent[i].getLow();
            }
        }

        return currentCandle.getLow() < lowestLow && currentCandle.getClose() > lowestLow;
    }

    /**
     * Detect 2B bearish pattern (higher high followed by lower low)
     */
    private boolean detect2BPatternBearish() {
        if (priceHistory.size() < config.getPattern2bLookback()) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - config.getPattern2bLookback()))
            .toArray(Candle[]::new);

        if (recent.length < 3) return false;

        double highestHigh = Double.MIN_VALUE;
        for (int i = 0; i < recent.length - 1; i++) {
            if (recent[i].getHigh() > highestHigh) {
                highestHigh = recent[i].getHigh();
            }
        }

        return currentCandle.getHigh() > highestHigh && currentCandle.getClose() < highestHigh;
    }

    /**
     * Detect double bottom pattern
     */
    private boolean detectDoubleBottom() {
        if (priceHistory.size() < config.getPatternDoubleLookback()) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - config.getPatternDoubleLookback()))
            .toArray(Candle[]::new);

        if (recent.length < 5) return false;

        int bottomCount = 0;
        double prevLow = Double.MAX_VALUE;

        for (int i = 1; i < recent.length - 1; i++) {
            if (recent[i].getLow() < recent[i - 1].getLow()
                && recent[i].getLow() < recent[i + 1].getLow()) {

                if (Math.abs(recent[i].getLow() - prevLow) / prevLow < 0.02) {
                    bottomCount++;
                }
                prevLow = recent[i].getLow();
            }
        }

        return bottomCount >= 2;
    }

    /**
     * Detect double top pattern
     */
    private boolean detectDoubleTop() {
        if (priceHistory.size() < config.getPatternDoubleLookback()) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - config.getPatternDoubleLookback()))
            .toArray(Candle[]::new);

        if (recent.length < 5) return false;

        int topCount = 0;
        double prevHigh = Double.MIN_VALUE;

        for (int i = 1; i < recent.length - 1; i++) {
            if (recent[i].getHigh() > recent[i - 1].getHigh()
                && recent[i].getHigh() > recent[i + 1].getHigh()) {

                if (Math.abs(recent[i].getHigh() - prevHigh) / prevHigh < 0.02) {
                    topCount++;
                }
                prevHigh = recent[i].getHigh();
            }
        }

        return topCount >= 2;
    }

    /**
     * Detect pullback down (for Long R5): price pulled back below EMA12
     */
    private boolean detectPullbackDown() {
        if (priceHistory.size() < 5) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - 5))
            .toArray(Candle[]::new);

        for (Candle c : recent) {
            if (c.getLow() < ema12.getValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detect pullback up (for Short R5): price pulled back above EMA12
     */
    private boolean detectPullbackUp() {
        if (priceHistory.size() < 5) return false;

        Candle[] recent = priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - 5))
            .toArray(Candle[]::new);

        for (Candle c : recent) {
            if (c.getHigh() > ema12.getValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if in bear trend (price < EMA144 < EMA169)
     */
    private boolean isBearTrend() {
        return currentCandle.getClose() < ema144.getValue()
            && ema144.getValue() < ema169.getValue();
    }

    /**
     * Check if in bull trend (price > EMA144 > EMA169)
     */
    private boolean isBullTrend() {
        return currentCandle.getClose() > ema144.getValue()
            && ema144.getValue() > ema169.getValue();
    }

    /**
     * Check if EMA144 golden cross EMA169
     */
    private boolean isGoldenCross() {
        return ema144.getValue() > ema169.getValue();
    }

    /**
     * Check if EMA144 death cross EMA169
     */
    private boolean isDeathCross() {
        return ema144.getValue() < ema169.getValue();
    }

    /**
     * Check if EMA12 is in middle zone (between EMA144 and EMA576)
     */
    private boolean isEma12InMiddleZone() {
        double ema12Val = ema12.getValue();
        double ema144Val = ema144.getValue();
        double ema576Val = ema576.getValue();

        return (ema12Val > ema144Val && ema12Val < ema576Val)
            || (ema12Val < ema144Val && ema12Val > ema576Val);
    }

    /**
     * Get the lowest low in the lookback period
     * @param lookback number of bars to look back
     * @return lowest low
     */
    public double getLowestLow(int lookback) {
        if (priceHistory.size() < lookback) {
            lookback = priceHistory.size();
        }

        return priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - lookback))
            .mapToDouble(Candle::getLow)
            .min()
            .orElse(currentCandle.getLow());
    }

    /**
     * Get the highest high in the lookback period
     * @param lookback number of bars to look back
     * @return highest high
     */
    public double getHighestHigh(int lookback) {
        if (priceHistory.size() < lookback) {
            lookback = priceHistory.size();
        }

        return priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - lookback))
            .mapToDouble(Candle::getHigh)
            .max()
            .orElse(currentCandle.getHigh());
    }

    /**
     * Get current ADX value
     * @return ADX value
     */
    public double getAdx() {
        return adx.getValue();
    }

    /**
     * Get ADX slope (rate of change)
     * @return slope value (positive = trend strengthening)
     */
    public double getAdxSlope() {
        return adx.getSlope();
    }

    /**
     * Check if ADX slope is positive
     * @return true if trend is strengthening
     */
    public boolean isAdxSlopeUp() {
        return adx.isSlopeUp();
    }

    /**
     * Get EMA12 value
     */
    public double getEma12() {
        return ema12.getValue();
    }

    /**
     * Get EMA144 value
     */
    public double getEma144() {
        return ema144.getValue();
    }

    /**
     * Get EMA169 value
     */
    public double getEma169() {
        return ema169.getValue();
    }

    /**
     * Get EMA576 value
     */
    public double getEma576() {
        return ema576.getValue();
    }

    /**
     * Get EMA676 value
     */
    public double getEma676() {
        return ema676.getValue();
    }

    /**
     * Get price history for analysis
     */
    public Deque<Candle> getPriceHistory() {
        return priceHistory;
    }

    /**
     * Calculate average volume over last N candles
     */
    public double getAverageVolume(int periods) {
        if (priceHistory.size() < periods) {
            periods = priceHistory.size();
        }
        if (periods == 0) return 0;

        return priceHistory.stream()
            .skip(Math.max(0, priceHistory.size() - periods))
            .mapToDouble(Candle::getVolume)
            .average()
            .orElse(0);
    }

    /**
     * Calculate ATR (Average True Range) over last N candles
     */
    public double getATR(int periods) {
        if (priceHistory.size() < periods + 1) return 0;

        Candle[] candles = priceHistory.toArray(new Candle[0]);
        double sum = 0;
        int count = 0;

        for (int i = candles.length - periods; i < candles.length; i++) {
            if (i <= 0) continue;
            Candle curr = candles[i];
            Candle prev = candles[i - 1];

            double tr = Math.max(
                curr.getHigh() - curr.getLow(),
                Math.max(
                    Math.abs(curr.getHigh() - prev.getClose()),
                    Math.abs(curr.getLow() - prev.getClose())
                )
            );
            sum += tr;
            count++;
        }

        return count > 0 ? sum / count : 0;
    }

    /**
     * Get current market regime
     * @return current market regime or null if not enabled
     */
    public MarketRegime.Regime getMarketRegime() {
        if (marketRegime != null && marketRegime.isReady()) {
            return marketRegime.getCurrentRegime();
        }
        return null;
    }

    /**
     * Get dynamic reward ratio based on market regime
     * @return reward ratio adjusted for market conditions
     */
    public double getDynamicRewardRatio() {
        if (!config.isEnableDynamicRewardRatio() || marketRegime == null || !marketRegime.isReady()) {
            // Use configured static reward ratio
            return Math.max(config.getRewardRatio(), config.getMinRewardRatio());
        }

        // Get regime-specific reward ratio
        MarketRegime.Regime regime = marketRegime.getCurrentRegime();
        switch (regime) {
            case STRONG_TREND:
                return config.getStrongTrendRewardRatio();
            case MODERATE_TREND:
                return config.getModerateTrendRewardRatio();
            case LOW_VOLATILITY_RANGE:
            case HIGH_VOLATILITY_RANGE:
                return config.getRangingRewardRatio();
            default:
                return Math.max(config.getRewardRatio(), config.getMinRewardRatio());
        }
    }

    /**
     * Get dynamic leverage based on market regime and ADX strength
     * Formula: baseLeverage × regimeMultiplier × adxFactor
     * @return leverage adjusted for market conditions
     */
    public int getDynamicLeverage() {
        if (!config.isEnableDynamicLeverage()) {
            // Return static leverage from config
            return config.getLeverage();
        }

        int baseLeverage = config.getBaseLeverage();
        double regimeMultiplier;
        double adxFactor;

        // Get regime-specific multiplier
        if (marketRegime != null && marketRegime.isReady()) {
            MarketRegime.Regime regime = marketRegime.getCurrentRegime();
            switch (regime) {
                case STRONG_TREND:
                    regimeMultiplier = config.getStrongTrendLeverageMultiplier();
                    break;
                case MODERATE_TREND:
                    regimeMultiplier = config.getModerateTrendLeverageMultiplier();
                    break;
                case LOW_VOLATILITY_RANGE:
                    regimeMultiplier = config.getLowVolRangeLeverageMultiplier();
                    break;
                case HIGH_VOLATILITY_RANGE:
                    regimeMultiplier = config.getHighVolRangeLeverageMultiplier();
                    break;
                default:
                    regimeMultiplier = 0.5;
            }
        } else {
            // Default to moderate if regime not available
            regimeMultiplier = config.getModerateTrendLeverageMultiplier();
        }

        // Calculate ADX strength factor (0.5 to 1.0 based on ADX value)
        // ADX >= strongTrendThreshold = 1.0
        // ADX = 0 = 0.5
        double adxValue = adx.getValue();
        double strongThreshold = config.getAdxStrongTrendThreshold();
        adxFactor = 0.5 + (0.5 * Math.min(1.0, adxValue / strongThreshold));

        // Calculate final leverage
        int dynamicLeverage = (int) Math.round(baseLeverage * regimeMultiplier * adxFactor);

        // Clamp to min/max bounds
        dynamicLeverage = Math.max(config.getMinLeverage(), dynamicLeverage);
        dynamicLeverage = Math.min(config.getMaxLeverage(), dynamicLeverage);

        return dynamicLeverage;
    }

    /**
     * Get leverage multiplier description for logging
     * @return description of current leverage calculation
     */
    public String getLeverageDescription() {
        if (!config.isEnableDynamicLeverage()) {
            return String.format("Static: %dx", config.getLeverage());
        }

        String regime = marketRegime != null && marketRegime.isReady()
            ? marketRegime.getCurrentRegime().toString()
            : "UNKNOWN";
        int leverage = getDynamicLeverage();
        return String.format("Dynamic: %dx (Regime: %s, ADX: %.1f)", leverage, regime, adx.getValue());
    }

    /**
     * Get market regime indicator (for external access)
     * @return MarketRegime indicator or null
     */
    public MarketRegime getMarketRegimeIndicator() {
        return marketRegime;
    }

    /**
     * Reset all strategy state
     */
    public void reset() {
        ema12.reset();
        ema144.reset();
        ema169.reset();
        ema576.reset();
        ema676.reset();
        adx.reset();
        if (marketRegime != null) {
            marketRegime.reset();
        }
        priceHistory.clear();
        previousCandle = null;
        currentCandle = null;
        wasAboveEma12 = false;
        wasBelowEma12 = false;
        lastTouchMinLongEmas = 0;
        lastTouchMaxShortEmas = 0;
        lastTouchMinMidEmas = 0;
        lastTouchMaxMidEmas = 0;
        crossCountAfterTouchLong = 0;
        crossCountAfterTouchShort = 0;
        crossCountAfterMidTouchLong = 0;
        crossCountAfterMidTouchShort = 0;
    }
}
