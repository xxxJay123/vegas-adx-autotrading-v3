package com.vegas.backtest.indicator;

/**
 * Exponential Moving Average - incremental implementation.
 * Zero allocation after warm-up period.
 */
public class EMA {

    private final int period;
    private final double multiplier;
    private double value;
    private boolean initialized;
    private int count;
    private double sum;

    /**
     * Create EMA with specified period
     * @param period EMA period
     */
    public EMA(int period) {
        this.period = period;
        this.multiplier = 2.0 / (period + 1);
        this.value = 0;
        this.initialized = false;
        this.count = 0;
        this.sum = 0;
    }

    /**
     * Update EMA with new price
     * @param price new price
     */
    public void update(double price) {
        if (!initialized) {
            sum += price;
            count++;

            if (count >= period) {
                value = sum / period;
                initialized = true;
            }
        } else {
            value = (price - value) * multiplier + value;
        }
    }

    /**
     * Get current EMA value
     * @return EMA value (0 if not initialized)
     */
    public double getValue() {
        return initialized ? value : 0;
    }

    /**
     * Check if EMA is ready
     * @return true if initialized
     */
    public boolean isReady() {
        return initialized;
    }

    /**
     * Reset indicator
     */
    public void reset() {
        value = 0;
        initialized = false;
        count = 0;
        sum = 0;
    }

    /**
     * Get the period
     * @return period
     */
    public int getPeriod() {
        return period;
    }
}
