package com.vegas.backtest.indicator;

import com.vegas.backtest.model.Candle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Average Directional Index (ADX) - incremental implementation.
 * Measures trend strength (0-100).
 */
public class ADX {

    private final int period;
    private final Deque<Candle> buffer;

    private double prevHigh;
    private double prevLow;
    private double prevClose;

    private double tr;
    private double plusDM;
    private double minusDM;

    private double atr;
    private double plusDI;
    private double minusDI;
    private double dx;
    private double adx;

    private boolean initialized;
    private int count;

    private final double alpha;

    // Slope detection
    private final Deque<Double> adxHistory;
    private static final int SLOPE_LOOKBACK = 5;  // Number of periods to calculate slope

    /**
     * Create ADX with specified period
     * @param period ADX period (typically 14)
     */
    public ADX(int period) {
        this.period = period;
        this.buffer = new ArrayDeque<>(period + 1);
        this.alpha = 1.0 / period;
        this.initialized = false;
        this.count = 0;
        this.adxHistory = new ArrayDeque<>(SLOPE_LOOKBACK + 1);
    }

    /**
     * Update ADX with new candle
     * @param candle new candle
     */
    public void update(Candle candle) {
        buffer.addLast(candle);
        if (buffer.size() > period + 1) {
            buffer.removeFirst();
        }

        if (count == 0) {
            prevHigh = candle.getHigh();
            prevLow = candle.getLow();
            prevClose = candle.getClose();
            count++;
            return;
        }

        double high = candle.getHigh();
        double low = candle.getLow();
        double close = candle.getClose();

        double currentTR = Math.max(
            high - low,
            Math.max(
                Math.abs(high - prevClose),
                Math.abs(low - prevClose)
            )
        );

        double currentPlusDM = (high - prevHigh) > (prevLow - low) && (high - prevHigh) > 0
            ? high - prevHigh
            : 0;

        double currentMinusDM = (prevLow - low) > (high - prevHigh) && (prevLow - low) > 0
            ? prevLow - low
            : 0;

        if (count < period) {
            tr = (tr * (count - 1) + currentTR) / count;
            plusDM = (plusDM * (count - 1) + currentPlusDM) / count;
            minusDM = (minusDM * (count - 1) + currentMinusDM) / count;
        } else {
            tr = tr - (tr * alpha) + currentTR * alpha;
            plusDM = plusDM - (plusDM * alpha) + currentPlusDM * alpha;
            minusDM = minusDM - (minusDM * alpha) + currentMinusDM * alpha;

            if (tr > 0) {
                plusDI = 100 * (plusDM / tr);
                minusDI = 100 * (minusDM / tr);

                double diSum = plusDI + minusDI;
                if (diSum > 0) {
                    dx = 100 * Math.abs(plusDI - minusDI) / diSum;

                    if (!initialized) {
                        adx = dx;
                        initialized = true;
                    } else {
                        adx = adx - (adx * alpha) + dx * alpha;
                    }

                    // Store ADX history for slope calculation
                    adxHistory.addLast(adx);
                    if (adxHistory.size() > SLOPE_LOOKBACK + 1) {
                        adxHistory.removeFirst();
                    }
                }
            }
        }

        prevHigh = high;
        prevLow = low;
        prevClose = close;
        count++;
    }

    /**
     * Get current ADX value
     * @return ADX value (0-100, 0 if not ready)
     */
    public double getValue() {
        return initialized ? adx : 0;
    }

    /**
     * Get +DI value
     * @return +DI value
     */
    public double getPlusDI() {
        return initialized ? plusDI : 0;
    }

    /**
     * Get -DI value
     * @return -DI value
     */
    public double getMinusDI() {
        return initialized ? minusDI : 0;
    }

    /**
     * Get ADX slope (rate of change)
     * Positive = trend strengthening, Negative = trend weakening
     * @return slope value (ADX change per period)
     */
    public double getSlope() {
        if (adxHistory.size() < 2) return 0;

        Double[] history = adxHistory.toArray(new Double[0]);
        double oldest = history[0];
        double newest = history[history.length - 1];

        // Slope = (newest - oldest) / periods
        return (newest - oldest) / (history.length - 1);
    }

    /**
     * Check if ADX slope is positive (trend strengthening)
     * @return true if slope > 0
     */
    public boolean isSlopeUp() {
        return getSlope() > 0;
    }

    /**
     * Check if ADX slope is negative (trend weakening)
     * @return true if slope < 0
     */
    public boolean isSlopeDown() {
        return getSlope() < 0;
    }

    /**
     * Check if ADX is ready
     * @return true if initialized
     */
    public boolean isReady() {
        return initialized;
    }

    /**
     * Reset indicator
     */
    public void reset() {
        buffer.clear();
        adxHistory.clear();
        initialized = false;
        count = 0;
        tr = 0;
        plusDM = 0;
        minusDM = 0;
        atr = 0;
        plusDI = 0;
        minusDI = 0;
        dx = 0;
        adx = 0;
    }

    /**
     * Get the period
     * @return period
     */
    public int getPeriod() {
        return period;
    }
}
