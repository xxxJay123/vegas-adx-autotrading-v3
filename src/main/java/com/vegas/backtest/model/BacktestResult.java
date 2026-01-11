package com.vegas.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains all results and statistics from a backtest run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {

    /**
     * Trading symbol
     */
    private String symbol;

    /**
     * Year tested
     */
    private int year;

    /**
     * Start timestamp
     */
    private long startTime;

    /**
     * End timestamp
     */
    private long endTime;

    /**
     * Initial balance
     */
    private double initialBalance;

    /**
     * Final balance
     */
    private double finalBalance;

    /**
     * All completed trades
     */
    @Builder.Default
    private List<Trade> trades = new ArrayList<>();

    /**
     * Equity curve (timestamp -> balance)
     */
    @Builder.Default
    private List<EquityPoint> equityCurve = new ArrayList<>();

    /**
     * Monthly returns (YYYY-MM -> return%)
     */
    @Builder.Default
    private List<MonthlyReturn> monthlyReturns = new ArrayList<>();

    /**
     * Get net profit in USDT
     * @return net profit
     */
    public double getNetProfit() {
        return finalBalance - initialBalance;
    }

    /**
     * Get net profit percentage
     * @return profit %
     */
    public double getNetProfitPercent() {
        return (finalBalance - initialBalance) / initialBalance * 100.0;
    }

    /**
     * Get total number of trades
     * @return trade count
     */
    public int getTotalTrades() {
        return trades.size();
    }

    /**
     * Get number of winning trades
     * @return winner count
     */
    public int getWinningTrades() {
        return (int) trades.stream().filter(Trade::isWinner).count();
    }

    /**
     * Get number of losing trades
     * @return loser count
     */
    public int getLosingTrades() {
        return getTotalTrades() - getWinningTrades();
    }

    /**
     * Get win rate percentage
     * @return win rate %
     */
    public double getWinRate() {
        if (getTotalTrades() == 0) return 0;
        return (double) getWinningTrades() / getTotalTrades() * 100.0;
    }

    /**
     * Get average winning trade
     * @return avg winner
     */
    public double getAverageWin() {
        return trades.stream()
            .filter(Trade::isWinner)
            .mapToDouble(Trade::getNetPnl)
            .average()
            .orElse(0.0);
    }

    /**
     * Get average losing trade
     * @return avg loser
     */
    public double getAverageLoss() {
        return trades.stream()
            .filter(t -> !t.isWinner())
            .mapToDouble(Trade::getNetPnl)
            .average()
            .orElse(0.0);
    }

    /**
     * Get profit factor (gross profit / gross loss)
     * @return profit factor
     */
    public double getProfitFactor() {
        double grossProfit = trades.stream()
            .filter(Trade::isWinner)
            .mapToDouble(Trade::getNetPnl)
            .sum();

        double grossLoss = Math.abs(trades.stream()
            .filter(t -> !t.isWinner())
            .mapToDouble(Trade::getNetPnl)
            .sum());

        if (grossLoss == 0) return grossProfit > 0 ? Double.POSITIVE_INFINITY : 0;
        return grossProfit / grossLoss;
    }

    /**
     * Get maximum drawdown percentage
     * @return max DD %
     */
    public double getMaxDrawdown() {
        if (equityCurve.isEmpty()) return 0;

        double maxDD = 0;
        double peak = equityCurve.get(0).getBalance();

        for (EquityPoint point : equityCurve) {
            if (point.getBalance() > peak) {
                peak = point.getBalance();
            }
            double dd = (peak - point.getBalance()) / peak * 100.0;
            if (dd > maxDD) {
                maxDD = dd;
            }
        }

        return maxDD;
    }

    /**
     * Get Sharpe ratio (annualized, assuming 252 trading days)
     * @return Sharpe ratio
     */
    public double getSharpeRatio() {
        if (trades.isEmpty()) return 0;

        double[] returns = trades.stream()
            .mapToDouble(t -> t.getNetPnl() / initialBalance)
            .toArray();

        double mean = 0;
        for (double r : returns) mean += r;
        mean /= returns.length;

        double variance = 0;
        for (double r : returns) {
            variance += Math.pow(r - mean, 2);
        }
        variance /= returns.length;

        double stdDev = Math.sqrt(variance);
        if (stdDev == 0) return 0;

        return (mean / stdDev) * Math.sqrt(252);
    }

    /**
     * Get total fees paid
     * @return total fees
     */
    public double getTotalFees() {
        return trades.stream().mapToDouble(Trade::getFees).sum();
    }

    /**
     * Represents a point on the equity curve
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquityPoint {
        private long timestamp;
        private double balance;
    }

    /**
     * Represents monthly return
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyReturn {
        private String month;
        private double returnPercent;
    }
}
