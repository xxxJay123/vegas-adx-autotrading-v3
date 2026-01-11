package com.vegas.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a completed trade with entry, exit, and PnL information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    /**
     * Trade direction
     */
    public enum Direction {
        LONG, SHORT
    }

    /**
     * Trade exit reason
     */
    public enum ExitReason {
        TAKE_PROFIT, STOP_LOSS, MAX_HOLDING_TIME
    }

    /**
     * Unique trade identifier
     */
    private int id;

    /**
     * Trading symbol
     */
    private String symbol;

    /**
     * Trade direction (LONG/SHORT)
     */
    private Direction direction;

    /**
     * Entry timestamp
     */
    private long entryTime;

    /**
     * Entry price
     */
    private double entryPrice;

    /**
     * Contract quantity (notional * leverage / entry_price)
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
     * Exit timestamp
     */
    private long exitTime;

    /**
     * Exit price
     */
    private double exitPrice;

    /**
     * Exit reason (TP/SL)
     */
    private ExitReason exitReason;

    /**
     * Profit/Loss in USDT
     */
    private double pnl;

    /**
     * Profit/Loss percentage
     */
    private double pnlPercent;

    /**
     * Total fees paid (entry + exit)
     */
    private double fees;

    /**
     * Net PnL after fees
     */
    private double netPnl;

    /**
     * Rule number that triggered this trade (1-8)
     */
    private int ruleNumber;

    /**
     * Leverage used for this trade (for dynamic leverage tracking)
     */
    private int leverage;

    /**
     * Notional value of this trade
     */
    private double notionalValue;

    /**
     * 交易入場時的完整市場狀態（用於分析虧損交易）
     */
    private TradeContext context;

    /**
     * Check if this trade was profitable
     * @return true if netPnl > 0
     */
    public boolean isWinner() {
        return netPnl > 0;
    }

    /**
     * Get the R-multiple (actual return / risk)
     * @return R-multiple
     */
    public double getRMultiple() {
        double risk = Math.abs(entryPrice - stopLoss);
        if (risk == 0) return 0;
        double reward = Math.abs(exitPrice - entryPrice);
        return direction == Direction.LONG
            ? reward / risk * (exitPrice > entryPrice ? 1 : -1)
            : reward / risk * (exitPrice < entryPrice ? 1 : -1);
    }

    /**
     * Get hold time in hours
     * @return hours held
     */
    public double getHoldTimeHours() {
        return (exitTime - entryTime) / 3600000.0;
    }
}
