package com.vegas.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single candlestick with OHLCV data.
 * Immutable value object for price data at a specific timestamp.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {

    /**
     * Timestamp in milliseconds since epoch
     */
    private long timestamp;

    /**
     * Opening price
     */
    private double open;

    /**
     * Highest price
     */
    private double high;

    /**
     * Lowest price
     */
    private double low;

    /**
     * Closing price
     */
    private double close;

    /**
     * Trading volume
     */
    private double volume;

    /**
     * Get the typical price (HLC/3)
     * @return typical price
     */
    public double getTypicalPrice() {
        return (high + low + close) / 3.0;
    }

    /**
     * Check if this is a bullish candle
     * @return true if close > open
     */
    public boolean isBullish() {
        return close > open;
    }

    /**
     * Check if this is a bearish candle
     * @return true if close < open
     */
    public boolean isBearish() {
        return close < open;
    }
}
