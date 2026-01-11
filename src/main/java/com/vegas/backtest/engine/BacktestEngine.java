package com.vegas.backtest.engine;

import com.vegas.backtest.config.Config;
import com.vegas.backtest.model.*;
import com.vegas.backtest.strategy.VegasADXStrategy;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core backtesting engine that executes strategy against historical data.
 */
@Slf4j
public class BacktestEngine {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    private final Config config;
    private final VegasADXStrategy strategy;

    private double balance;
    private Position currentPosition;
    private TradeContext currentTradeContext;  // 記錄當前交易入場時的市場狀態
    private final List<Trade> completedTrades;
    private final List<BacktestResult.EquityPoint> equityCurve;
    private int tradeIdCounter;
    private int consecutiveWins;
    private int consecutiveLosses;
    private long lastTradeTime;

    /**
     * Create backtest engine
     * @param config configuration
     */
    public BacktestEngine(Config config) {
        this.config = config;
        this.strategy = new VegasADXStrategy(config);
        this.completedTrades = new ArrayList<>();
        this.equityCurve = new ArrayList<>();
        this.tradeIdCounter = 1;
    }

    /**
     * Run backtest on candle data
     * @param symbol trading symbol
     * @param year year being tested
     * @param candles historical candle data
     * @param initialBalance starting balance
     * @return backtest results
     */
    public BacktestResult runBacktest(String symbol, int year, List<Candle> candles, double initialBalance) {
        log.info("Starting backtest for {} year {}", symbol, year);
        log.info("Candles: {}, Initial Balance: {} USDT", candles.size(), initialBalance);

        this.balance = initialBalance;
        this.currentPosition = null;
        this.currentTradeContext = null;
        this.completedTrades.clear();
        this.equityCurve.clear();
        this.tradeIdCounter = 1;
        this.consecutiveWins = 0;
        this.consecutiveLosses = 0;
        this.lastTradeTime = 0;
        this.strategy.reset();

        long startTime = candles.get(0).getTimestamp();
        long endTime = candles.get(candles.size() - 1).getTimestamp();

        for (int i = 0; i < candles.size(); i++) {
            Candle candle = candles.get(i);
            strategy.update(candle);

            if (currentPosition != null) {
                checkExit(candle);
            }

            if (currentPosition == null && strategy.isReady()) {
                checkEntry(candle, symbol);
            }

            if (i % 1000 == 0) {
                equityCurve.add(BacktestResult.EquityPoint.builder()
                    .timestamp(candle.getTimestamp())
                    .balance(balance)
                    .build());
            }
        }

        equityCurve.add(BacktestResult.EquityPoint.builder()
            .timestamp(endTime)
            .balance(balance)
            .build());

        List<BacktestResult.MonthlyReturn> monthlyReturns = calculateMonthlyReturns(candles, initialBalance);

        BacktestResult result = BacktestResult.builder()
            .symbol(symbol)
            .year(year)
            .startTime(startTime)
            .endTime(endTime)
            .initialBalance(initialBalance)
            .finalBalance(balance)
            .trades(new ArrayList<>(completedTrades))
            .equityCurve(new ArrayList<>(equityCurve))
            .monthlyReturns(monthlyReturns)
            .build();

        log.info("Backtest completed: {} trades, Net Profit: {:.2f} USDT ({:.2f}%)",
            result.getTotalTrades(), result.getNetProfit(), result.getNetProfitPercent());

        return result;
    }

    /**
     * Check for entry signals and open position
     */
    private void checkEntry(Candle candle, String symbol) {
        int longRule = strategy.checkLongEntry();
        int shortRule = strategy.checkShortEntry();

        if (longRule > 0) {
            openPosition(Trade.Direction.LONG, candle, symbol, longRule);
        } else if (shortRule > 0) {
            openPosition(Trade.Direction.SHORT, candle, symbol, shortRule);
        }
    }

    /**
     * Open a new position
     */
    private void openPosition(Trade.Direction direction, Candle candle, String symbol, int ruleNumber) {
        double entryPrice = candle.getClose();

        // Calculate stop loss
        double stopLoss;
        if (direction == Trade.Direction.LONG) {
            stopLoss = strategy.getLowestLow(config.getStopLookback());
        } else {
            stopLoss = strategy.getHighestHigh(config.getStopLookback());
        }

        double risk = Math.abs(entryPrice - stopLoss);

        // Get dynamic leverage based on market regime
        int effectiveLeverage = strategy.getDynamicLeverage();

        // Calculate position quantity based on position sizing mode
        double quantity;
        double notionalValue;

        if (config.isEnableFixedRiskSizing()) {
            // Fixed Risk Sizing: Each loss = fixedRiskPerTradeUsdt
            // With dynamic leverage, we adjust position size based on market conditions
            // Base risk is fixedRiskPerTradeUsdt, adjusted by leverage ratio
            double leverageRatio = (double) effectiveLeverage / config.getBaseLeverage();
            double adjustedRisk = config.getFixedRiskPerTradeUsdt() * leverageRatio;
            quantity = adjustedRisk / risk;
            notionalValue = quantity * entryPrice;
        } else {
            // Traditional Fixed Notional Sizing with dynamic leverage
            notionalValue = config.getFixedNotionalUsdt() * effectiveLeverage;
            quantity = notionalValue / entryPrice;
        }

        // Get dynamic reward ratio based on market regime
        double effectiveRewardRatio;
        if (config.isEnableDynamicRewardRatio()) {
            effectiveRewardRatio = strategy.getDynamicRewardRatio();
        } else {
            effectiveRewardRatio = Math.max(config.getRewardRatio(), config.getMinRewardRatio());
        }

        double reward = risk * effectiveRewardRatio;

        double takeProfit;
        if (direction == Trade.Direction.LONG) {
            takeProfit = entryPrice + reward;
        } else {
            takeProfit = entryPrice - reward;
        }

        double entryFee = notionalValue * (config.getTakerFeePercent() / 100.0);

        // 記錄入場時的完整市場狀態
        currentTradeContext = buildTradeContext(candle, stopLoss, takeProfit);

        currentPosition = Position.builder()
            .direction(direction)
            .entryTime(candle.getTimestamp())
            .entryPrice(entryPrice)
            .quantity(quantity)
            .stopLoss(stopLoss)
            .takeProfit(takeProfit)
            .ruleNumber(ruleNumber)
            .entryFee(entryFee)
            .leverage(effectiveLeverage)
            .notionalValue(notionalValue)
            .build();

        log.debug("Opened {} position at {} | SL: {} | TP: {} | Rule: {} | Leverage: {}x",
            direction, entryPrice, stopLoss, takeProfit, ruleNumber, effectiveLeverage);
    }

    /**
     * 構建交易入場時的市場狀態記錄
     */
    private TradeContext buildTradeContext(Candle candle, double stopLoss, double takeProfit) {
        ZonedDateTime zdt = Instant.ofEpochMilli(candle.getTimestamp()).atZone(ZoneId.of("UTC"));

        double entryPrice = candle.getClose();
        double avgVolume = strategy.getAverageVolume(20);
        double atr = strategy.getATR(14);

        // K 線形態分析
        double candleRange = candle.getHigh() - candle.getLow();
        double bodySize = Math.abs(candle.getClose() - candle.getOpen());
        double upperWick = candle.getHigh() - Math.max(candle.getClose(), candle.getOpen());
        double lowerWick = Math.min(candle.getClose(), candle.getOpen()) - candle.getLow();

        // 計算與上一筆交易的間隔
        double hoursSinceLastTrade = lastTradeTime > 0
            ? (candle.getTimestamp() - lastTradeTime) / 3600000.0
            : -1;

        return TradeContext.builder()
            // 時間相關
            .entryHour(zdt.getHour())
            .entryDayOfWeek(zdt.getDayOfWeek().getValue())
            .entryMonth(zdt.getMonthValue())

            // 趨勢指標
            .adx(strategy.getAdx())
            .ema12(strategy.getEma12())
            .ema144(strategy.getEma144())
            .ema169(strategy.getEma169())
            .ema576(strategy.getEma576())
            .ema676(strategy.getEma676())

            // 價格相關
            .entryPrice(entryPrice)
            .entryVolume(candle.getVolume())
            .avgVolume(avgVolume)
            .volumeRatio(avgVolume > 0 ? candle.getVolume() / avgVolume : 1.0)
            .stopLossDistancePercent(Math.abs(entryPrice - stopLoss) / entryPrice * 100)
            .takeProfitDistancePercent(Math.abs(takeProfit - entryPrice) / entryPrice * 100)

            // 波動性相關
            .atr(atr)
            .atrPercent(entryPrice > 0 ? atr / entryPrice * 100 : 0)
            .recentRange(candleRange)

            // EMA 結構相關
            .distanceFromEma12Percent((entryPrice - strategy.getEma12()) / entryPrice * 100)
            .distanceFromEma144Percent((entryPrice - strategy.getEma144()) / entryPrice * 100)
            .ema144To169DistancePercent((strategy.getEma144() - strategy.getEma169()) / strategy.getEma169() * 100)
            .goldenCross(strategy.getEma144() > strategy.getEma169())
            .deathCross(strategy.getEma144() < strategy.getEma169())

            // K 線形態相關
            .bullishCandle(candle.isBullish())
            .candleBodyPercent(candleRange > 0 ? bodySize / candleRange * 100 : 0)
            .upperWickPercent(candleRange > 0 ? upperWick / candleRange * 100 : 0)
            .lowerWickPercent(candleRange > 0 ? lowerWick / candleRange * 100 : 0)

            // 連續性相關
            .consecutiveWins(consecutiveWins)
            .consecutiveLosses(consecutiveLosses)
            .hoursSinceLastTrade(hoursSinceLastTrade)

            .build();
    }

    /**
     * Check if position should exit
     */
    private void checkExit(Candle candle) {
        if (currentPosition == null) return;

        boolean hitSL = currentPosition.isStopLossHit(candle.getLow(), candle.getHigh());
        boolean hitTP = currentPosition.isTakeProfitHit(candle.getLow(), candle.getHigh());

        // Check max holding time if enabled
        boolean exceededMaxHoldingTime = false;
        if (config.isEnableMaxHoldingTime()) {
            long holdingTimeMs = candle.getTimestamp() - currentPosition.getEntryTime();
            long maxHoldingTimeMs = config.getMaxHoldingTimeHours() * 3600000L;
            if (holdingTimeMs > maxHoldingTimeMs) {
                exceededMaxHoldingTime = true;
            }
        }

        if (hitTP) {
            closePosition(currentPosition.getTakeProfit(), Trade.ExitReason.TAKE_PROFIT, candle.getTimestamp());
        } else if (hitSL) {
            closePosition(currentPosition.getStopLoss(), Trade.ExitReason.STOP_LOSS, candle.getTimestamp());
        } else if (exceededMaxHoldingTime) {
            // Exit at current close price due to max holding time
            closePosition(candle.getClose(), Trade.ExitReason.MAX_HOLDING_TIME, candle.getTimestamp());
        }
    }

    /**
     * Close current position and record trade
     */
    private void closePosition(double exitPrice, Trade.ExitReason exitReason, long exitTime) {
        if (currentPosition == null) return;

        double pnl;
        if (currentPosition.getDirection() == Trade.Direction.LONG) {
            pnl = (exitPrice - currentPosition.getEntryPrice()) * currentPosition.getQuantity();
        } else {
            pnl = (currentPosition.getEntryPrice() - exitPrice) * currentPosition.getQuantity();
        }

        // Use position's notional value for PNL calculation (important for dynamic leverage)
        double positionNotional = currentPosition.getNotionalValue();
        double pnlPercent = positionNotional > 0 ? (pnl / positionNotional) * 100.0 : 0;

        double exitFee = positionNotional
            * (exitReason == Trade.ExitReason.TAKE_PROFIT
            ? config.getMakerFeePercent() / 100.0
            : config.getTakerFeePercent() / 100.0);

        double totalFees = currentPosition.getEntryFee() + exitFee;
        double netPnl = pnl - totalFees;

        Trade trade = Trade.builder()
            .id(tradeIdCounter++)
            .symbol("")
            .direction(currentPosition.getDirection())
            .entryTime(currentPosition.getEntryTime())
            .entryPrice(currentPosition.getEntryPrice())
            .quantity(currentPosition.getQuantity())
            .stopLoss(currentPosition.getStopLoss())
            .takeProfit(currentPosition.getTakeProfit())
            .exitTime(exitTime)
            .exitPrice(exitPrice)
            .exitReason(exitReason)
            .pnl(pnl)
            .pnlPercent(pnlPercent)
            .fees(totalFees)
            .netPnl(netPnl)
            .ruleNumber(currentPosition.getRuleNumber())
            .leverage(currentPosition.getLeverage())
            .notionalValue(positionNotional)
            .context(currentTradeContext)  // 記錄入場時的市場狀態
            .build();

        completedTrades.add(trade);
        balance += netPnl;

        // 更新連續盈虧計數
        if (netPnl > 0) {
            consecutiveWins++;
            consecutiveLosses = 0;
        } else {
            consecutiveLosses++;
            consecutiveWins = 0;
        }
        lastTradeTime = exitTime;

        log.debug("Closed {} trade | Exit: {} ({}) | PnL: {:.2f} USDT | Balance: {:.2f} USDT",
            trade.getDirection(), exitPrice, exitReason, netPnl, balance);

        currentPosition = null;
        currentTradeContext = null;
    }

    /**
     * Calculate monthly returns
     */
    private List<BacktestResult.MonthlyReturn> calculateMonthlyReturns(List<Candle> candles, double initialBalance) {
        Map<String, List<Trade>> tradesByMonth = new HashMap<>();

        for (Trade trade : completedTrades) {
            String month = Instant.ofEpochMilli(trade.getEntryTime())
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));

            tradesByMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(trade);
        }

        List<BacktestResult.MonthlyReturn> monthlyReturns = new ArrayList<>();
        double runningBalance = initialBalance;

        for (Map.Entry<String, List<Trade>> entry : tradesByMonth.entrySet()) {
            double monthPnl = entry.getValue().stream()
                .mapToDouble(Trade::getNetPnl)
                .sum();

            double monthReturn = (monthPnl / runningBalance) * 100.0;
            runningBalance += monthPnl;

            monthlyReturns.add(BacktestResult.MonthlyReturn.builder()
                .month(entry.getKey())
                .returnPercent(monthReturn)
                .build());
        }

        monthlyReturns.sort((a, b) -> a.getMonth().compareTo(b.getMonth()));

        return monthlyReturns;
    }
}
