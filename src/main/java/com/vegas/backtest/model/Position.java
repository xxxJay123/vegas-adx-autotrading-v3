package com.vegas.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an active position that hasn't been closed yet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    /**
     * Trade direction
     */
    private Trade.Direction direction;

    /**
     * Entry timestamp
     */
    private long entryTime;

    /**
     * Entry price
     */
    private double entryPrice;

    /**
     * Contract quantity
     */
    private double quantity;

    /**
     * Stop loss price
     */
    private double stopLoss;

    /**
     * Take profit price
     */
    private double takeProfit;

    /**
     * Rule number that triggered this position
     */
    private int ruleNumber;

    /**
     * Entry fee paid
     */
    private double entryFee;

    /**
     * Leverage used for this position (for dynamic leverage tracking)
     */
    private int leverage;

    /**
     * Notional value of this position
     */
    private double notionalValue;

    /**
     * Check if stop loss is hit
     * @param currentLow current bar's low price
     * @param currentHigh current bar's high price
     * @return true if SL hit
     */
    public boolean isStopLossHit(double currentLow, double currentHigh) {
        if (direction == Trade.Direction.LONG) {
            return currentLow <= stopLoss;
        } else {
            return currentHigh >= stopLoss;
        }
    }

    /**
     * Check if take profit is hit
     * @param currentLow current bar's low price
     * @param currentHigh current bar's high price
     * @return true if TP hit
     */
    public boolean isTakeProfitHit(double currentLow, double currentHigh) {
        if (direction == Trade.Direction.LONG) {
            return currentHigh >= takeProfit;
        } else {
            return currentLow <= takeProfit;
        }
    }

    /**
     * Calculate unrealized PnL
     * @param currentPrice current market price
     * @return unrealized PnL in USDT
     */
    public double getUnrealizedPnl(double currentPrice) {
        if (direction == Trade.Direction.LONG) {
            return (currentPrice - entryPrice) * quantity;
        } else {
            return (entryPrice - currentPrice) * quantity;
        }
    }
}
