package com.vegas.backtest.indicator;

import com.vegas.backtest.model.Candle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Market Regime Detection Indicator.
 * Identifies market conditions: Strong Trend, Moderate Trend, Low Volatility Range, High Volatility Range.
 * Used to dynamically adjust trading parameters based on market conditions.
 */
public class MarketRegime {

    public enum Regime {
        STRONG_TREND,           // ADX >= strongTrendThreshold (e.g., 40)
        MODERATE_TREND,         // ADX between moderateTrendThreshold and strongTrendThreshold
        LOW_VOLATILITY_RANGE,   // ADX < moderateTrendThreshold, narrow BB width
        HIGH_VOLATILITY_RANGE   // ADX < moderateTrendThreshold, wide BB width - DANGEROUS
    }

    private final double strongTrendThreshold;
    private final double moderateTrendThreshold;
    private final int atrPeriod;
    private final int bbPeriod;
    private final double bbMultiplier;

    // ATR for volatility measurement
    private final Deque<Double> trValues;
    private double atr;

    // Bollinger Band width calculation
    private final Deque<Double> closePrices;
    private double sma;
    private double bbWidth;

    // Historical ATR for ratio calculation
    private final Deque<Double> atrHistory;
    private static final int ATR_HISTORY_PERIOD = 50;

    private Regime currentRegime;
    private boolean ready;
    private int count;

    private double prevClose;

    /**
     * Create MarketRegime detector with custom thresholds
     * @param strongTrendThreshold ADX threshold for strong trend (e.g., 40)
     * @param moderateTrendThreshold ADX threshold for moderate trend (e.g., 25)
     */
    public MarketRegime(double strongTrendThreshold, double moderateTrendThreshold) {
        this.strongTrendThreshold = strongTrendThreshold;
        this.moderateTrendThreshold = moderateTrendThreshold;
        this.atrPeriod = 14;
        this.bbPeriod = 20;
        this.bbMultiplier = 2.0;

        this.trValues = new ArrayDeque<>(atrPeriod + 1);
        this.closePrices = new ArrayDeque<>(bbPeriod + 1);
        this.atrHistory = new ArrayDeque<>(ATR_HISTORY_PERIOD + 1);

        this.currentRegime = Regime.LOW_VOLATILITY_RANGE;
        this.ready = false;
        this.count = 0;
    }

    /**
     * Create MarketRegime detector with default thresholds (40/25)
     */
    public MarketRegime() {
        this(40.0, 25.0);
    }

    /**
     * Update regime detection with new candle and ADX value
     * @param candle new candle
     * @param adxValue current ADX value from ADX indicator
     */
    public void update(Candle candle, double adxValue) {
        count++;

        // Calculate True Range
        double tr;
        if (count == 1) {
            tr = candle.getHigh() - candle.getLow();
            prevClose = candle.getClose();
        } else {
            tr = Math.max(
                candle.getHigh() - candle.getLow(),
                Math.max(
                    Math.abs(candle.getHigh() - prevClose),
                    Math.abs(candle.getLow() - prevClose)
                )
            );
            prevClose = candle.getClose();
        }

        // Update ATR
        trValues.addLast(tr);
        if (trValues.size() > atrPeriod) {
            trValues.removeFirst();
        }
        if (trValues.size() >= atrPeriod) {
            atr = trValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            atrHistory.addLast(atr);
            if (atrHistory.size() > ATR_HISTORY_PERIOD) {
                atrHistory.removeFirst();
            }
        }

        // Update Bollinger Band width
        closePrices.addLast(candle.getClose());
        if (closePrices.size() > bbPeriod) {
            closePrices.removeFirst();
        }
        if (closePrices.size() >= bbPeriod) {
            sma = closePrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = closePrices.stream()
                .mapToDouble(p -> Math.pow(p - sma, 2))
                .average()
                .orElse(0);
            double stdDev = Math.sqrt(variance);
            double upperBand = sma + (bbMultiplier * stdDev);
            double lowerBand = sma - (bbMultiplier * stdDev);
            bbWidth = (upperBand - lowerBand) / sma * 100; // Width as percentage
        }

        // Determine regime
        if (count >= Math.max(atrPeriod, bbPeriod)) {
            ready = true;
            currentRegime = classifyRegime(adxValue);
        }
    }

    /**
     * Classify market regime based on ADX and volatility
     */
    private Regime classifyRegime(double adxValue) {
        if (adxValue >= strongTrendThreshold) {
            return Regime.STRONG_TREND;
        } else if (adxValue >= moderateTrendThreshold) {
            return Regime.MODERATE_TREND;
        } else {
            // Ranging market - check volatility
            double avgAtr = getAverageAtr();
            double atrRatio = avgAtr > 0 ? atr / avgAtr : 1.0;

            // High volatility range: ATR ratio > 1.3 OR BB width > 6%
            if (atrRatio > 1.3 || bbWidth > 6.0) {
                return Regime.HIGH_VOLATILITY_RANGE;
            } else {
                return Regime.LOW_VOLATILITY_RANGE;
            }
        }
    }

    /**
     * Get average ATR from history
     */
    private double getAverageAtr() {
        if (atrHistory.isEmpty()) return atr;
        return atrHistory.stream().mapToDouble(Double::doubleValue).average().orElse(atr);
    }

    /**
     * Get current market regime
     * @return current regime
     */
    public Regime getCurrentRegime() {
        return currentRegime;
    }

    /**
     * Check if trading should be paused (high volatility ranging)
     * @return true if should pause trading
     */
    public boolean shouldPauseTrading() {
        return currentRegime == Regime.HIGH_VOLATILITY_RANGE;
    }

    /**
     * Get recommended reward ratio based on current regime
     * @return recommended R:R ratio
     */
    public double getRecommendedRewardRatio() {
        switch (currentRegime) {
            case STRONG_TREND:
                return 3.7;
            case MODERATE_TREND:
                return 2.5;
            case LOW_VOLATILITY_RANGE:
                return 1.8;
            case HIGH_VOLATILITY_RANGE:
            default:
                return 1.5;
        }
    }

    /**
     * Check if indicator is ready
     * @return true if ready
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Get current ATR value
     * @return ATR
     */
    public double getAtr() {
        return atr;
    }

    /**
     * Get current Bollinger Band width as percentage
     * @return BB width %
     */
    public double getBbWidth() {
        return bbWidth;
    }

    /**
     * Get ATR ratio (current ATR / average ATR)
     * @return ATR ratio
     */
    public double getAtrRatio() {
        double avgAtr = getAverageAtr();
        return avgAtr > 0 ? atr / avgAtr : 1.0;
    }

    /**
     * Reset indicator
     */
    public void reset() {
        trValues.clear();
        closePrices.clear();
        atrHistory.clear();
        atr = 0;
        sma = 0;
        bbWidth = 0;
        currentRegime = Regime.LOW_VOLATILITY_RANGE;
        ready = false;
        count = 0;
        prevClose = 0;
    }

    @Override
    public String toString() {
        return String.format("MarketRegime[%s, ATR=%.2f, ATRRatio=%.2f, BBWidth=%.2f%%]",
            currentRegime, atr, getAtrRatio(), bbWidth);
    }
}
