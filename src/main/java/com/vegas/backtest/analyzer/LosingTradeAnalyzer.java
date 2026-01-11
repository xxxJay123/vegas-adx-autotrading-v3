package com.vegas.backtest.analyzer;

import com.vegas.backtest.model.Trade;
import com.vegas.backtest.model.TradeContext;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 虧損交易分析器
 * 分析輸的場次的共通點，找出可優化的模式
 */
@Slf4j
public class LosingTradeAnalyzer {

    private final List<Trade> allTrades;
    private final List<Trade> losingTrades;
    private final List<Trade> winningTrades;

    public LosingTradeAnalyzer(List<Trade> trades) {
        this.allTrades = trades;
        this.losingTrades = trades.stream()
            .filter(t -> !t.isWinner())
            .collect(Collectors.toList());
        this.winningTrades = trades.stream()
            .filter(Trade::isWinner)
            .collect(Collectors.toList());
    }

    /**
     * 執行完整分析並生成報告
     */
    public AnalysisReport analyze() {
        if (losingTrades.isEmpty()) {
            log.info("No losing trades to analyze!");
            return AnalysisReport.builder()
                .totalTrades(allTrades.size())
                .losingTrades(0)
                .winningTrades(winningTrades.size())
                .build();
        }

        log.info("Analyzing {} losing trades out of {} total trades...",
            losingTrades.size(), allTrades.size());

        AnalysisReport report = AnalysisReport.builder()
            .totalTrades(allTrades.size())
            .losingTrades(losingTrades.size())
            .winningTrades(winningTrades.size())
            .winRate(winningTrades.size() * 100.0 / allTrades.size())

            // 時間分析
            .hourAnalysis(analyzeByHour())
            .dayOfWeekAnalysis(analyzeByDayOfWeek())
            .monthAnalysis(analyzeByMonth())
            .sessionAnalysis(analyzeBySession())

            // 指標分析
            .adxAnalysis(analyzeADX())
            .volumeAnalysis(analyzeVolume())
            .atrAnalysis(analyzeATR())

            // EMA 結構分析
            .emaStructureAnalysis(analyzeEMAStructure())
            .distanceFromEmaAnalysis(analyzeDistanceFromEMA())

            // K 線形態分析
            .candlePatternAnalysis(analyzeCandlePatterns())

            // 規則分析
            .ruleAnalysis(analyzeByRule())

            // 連續性分析
            .consecutiveAnalysis(analyzeConsecutive())

            // 止損距離分析
            .stopLossAnalysis(analyzeStopLossDistance())

            // 關鍵發現
            .keyFindings(generateKeyFindings())

            .build();

        return report;
    }

    /**
     * 按小時分析
     */
    private Map<Integer, HourStats> analyzeByHour() {
        Map<Integer, HourStats> result = new TreeMap<>();

        for (int hour = 0; hour < 24; hour++) {
            final int h = hour;
            long totalAtHour = allTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryHour() == h)
                .count();
            long lossesAtHour = losingTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryHour() == h)
                .count();

            if (totalAtHour > 0) {
                double lossRate = lossesAtHour * 100.0 / totalAtHour;
                double avgLoss = losingTrades.stream()
                    .filter(t -> t.getContext() != null && t.getContext().getEntryHour() == h)
                    .mapToDouble(Trade::getNetPnl)
                    .average()
                    .orElse(0);

                result.put(hour, HourStats.builder()
                    .hour(hour)
                    .totalTrades((int) totalAtHour)
                    .losses((int) lossesAtHour)
                    .lossRate(lossRate)
                    .avgLoss(avgLoss)
                    .build());
            }
        }

        return result;
    }

    /**
     * 按星期幾分析
     */
    private Map<String, DayStats> analyzeByDayOfWeek() {
        Map<String, DayStats> result = new LinkedHashMap<>();

        for (int day = 1; day <= 7; day++) {
            final int d = day;
            String dayName = DayOfWeek.of(day).toString();

            long totalAtDay = allTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryDayOfWeek() == d)
                .count();
            long lossesAtDay = losingTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryDayOfWeek() == d)
                .count();

            if (totalAtDay > 0) {
                double lossRate = lossesAtDay * 100.0 / totalAtDay;
                double totalLoss = losingTrades.stream()
                    .filter(t -> t.getContext() != null && t.getContext().getEntryDayOfWeek() == d)
                    .mapToDouble(Trade::getNetPnl)
                    .sum();

                result.put(dayName, DayStats.builder()
                    .dayOfWeek(dayName)
                    .totalTrades((int) totalAtDay)
                    .losses((int) lossesAtDay)
                    .lossRate(lossRate)
                    .totalLoss(totalLoss)
                    .build());
            }
        }

        return result;
    }

    /**
     * 按月份分析
     */
    private Map<Integer, MonthStats> analyzeByMonth() {
        Map<Integer, MonthStats> result = new TreeMap<>();

        for (int month = 1; month <= 12; month++) {
            final int m = month;

            long totalAtMonth = allTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryMonth() == m)
                .count();
            long lossesAtMonth = losingTrades.stream()
                .filter(t -> t.getContext() != null && t.getContext().getEntryMonth() == m)
                .count();

            if (totalAtMonth > 0) {
                double lossRate = lossesAtMonth * 100.0 / totalAtMonth;

                result.put(month, MonthStats.builder()
                    .month(month)
                    .totalTrades((int) totalAtMonth)
                    .losses((int) lossesAtMonth)
                    .lossRate(lossRate)
                    .build());
            }
        }

        return result;
    }

    /**
     * 按交易時段分析
     */
    private Map<String, SessionStats> analyzeBySession() {
        Map<String, SessionStats> result = new LinkedHashMap<>();

        // 亞洲時段 (00:00 - 08:00 UTC)
        result.put("Asian", analyzeSession("Asian", ctx -> ctx.getEntryHour() >= 0 && ctx.getEntryHour() < 8));
        // 歐洲時段 (08:00 - 16:00 UTC)
        result.put("European", analyzeSession("European", ctx -> ctx.getEntryHour() >= 8 && ctx.getEntryHour() < 16));
        // 美國時段 (16:00 - 24:00 UTC)
        result.put("US", analyzeSession("US", ctx -> ctx.getEntryHour() >= 16 && ctx.getEntryHour() < 24));

        return result;
    }

    private SessionStats analyzeSession(String name, Predicate<TradeContext> filter) {
        long total = allTrades.stream()
            .filter(t -> t.getContext() != null && filter.test(t.getContext()))
            .count();
        long losses = losingTrades.stream()
            .filter(t -> t.getContext() != null && filter.test(t.getContext()))
            .count();

        double lossRate = total > 0 ? losses * 100.0 / total : 0;
        double totalLoss = losingTrades.stream()
            .filter(t -> t.getContext() != null && filter.test(t.getContext()))
            .mapToDouble(Trade::getNetPnl)
            .sum();

        return SessionStats.builder()
            .session(name)
            .totalTrades((int) total)
            .losses((int) losses)
            .lossRate(lossRate)
            .totalLoss(totalLoss)
            .build();
    }

    /**
     * 分析 ADX 值的影響
     */
    private ADXAnalysis analyzeADX() {
        // 計算虧損交易和盈利交易的 ADX 平均值
        double avgADXLosing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getAdx())
            .average()
            .orElse(0);

        double avgADXWinning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getAdx())
            .average()
            .orElse(0);

        // 按 ADX 區間分析
        Map<String, Double> lossRateByADXRange = new LinkedHashMap<>();
        lossRateByADXRange.put("48-50", calculateLossRate(t -> t.getContext().getAdx() >= 48 && t.getContext().getAdx() < 50));
        lossRateByADXRange.put("50-55", calculateLossRate(t -> t.getContext().getAdx() >= 50 && t.getContext().getAdx() < 55));
        lossRateByADXRange.put("55-60", calculateLossRate(t -> t.getContext().getAdx() >= 55 && t.getContext().getAdx() < 60));
        lossRateByADXRange.put("60-70", calculateLossRate(t -> t.getContext().getAdx() >= 60 && t.getContext().getAdx() < 70));
        lossRateByADXRange.put("70+", calculateLossRate(t -> t.getContext().getAdx() >= 70));

        return ADXAnalysis.builder()
            .avgADXLosing(avgADXLosing)
            .avgADXWinning(avgADXWinning)
            .lossRateByADXRange(lossRateByADXRange)
            .build();
    }

    /**
     * 分析成交量的影響
     */
    private VolumeAnalysis analyzeVolume() {
        double avgVolumeRatioLosing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getVolumeRatio())
            .average()
            .orElse(0);

        double avgVolumeRatioWinning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getVolumeRatio())
            .average()
            .orElse(0);

        // 按成交量區間分析
        Map<String, Double> lossRateByVolumeRange = new LinkedHashMap<>();
        lossRateByVolumeRange.put("Very Low (<0.5x)", calculateLossRate(t -> t.getContext().getVolumeRatio() < 0.5));
        lossRateByVolumeRange.put("Low (0.5-0.8x)", calculateLossRate(t -> t.getContext().getVolumeRatio() >= 0.5 && t.getContext().getVolumeRatio() < 0.8));
        lossRateByVolumeRange.put("Normal (0.8-1.2x)", calculateLossRate(t -> t.getContext().getVolumeRatio() >= 0.8 && t.getContext().getVolumeRatio() < 1.2));
        lossRateByVolumeRange.put("High (1.2-2x)", calculateLossRate(t -> t.getContext().getVolumeRatio() >= 1.2 && t.getContext().getVolumeRatio() < 2.0));
        lossRateByVolumeRange.put("Very High (>2x)", calculateLossRate(t -> t.getContext().getVolumeRatio() >= 2.0));

        return VolumeAnalysis.builder()
            .avgVolumeRatioLosing(avgVolumeRatioLosing)
            .avgVolumeRatioWinning(avgVolumeRatioWinning)
            .lossRateByVolumeRange(lossRateByVolumeRange)
            .build();
    }

    /**
     * 分析 ATR (波動性) 的影響
     */
    private ATRAnalysis analyzeATR() {
        double avgATRPercentLosing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getAtrPercent())
            .average()
            .orElse(0);

        double avgATRPercentWinning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getAtrPercent())
            .average()
            .orElse(0);

        return ATRAnalysis.builder()
            .avgATRPercentLosing(avgATRPercentLosing)
            .avgATRPercentWinning(avgATRPercentWinning)
            .build();
    }

    /**
     * 分析 EMA 結構的影響
     */
    private EMAStructureAnalysis analyzeEMAStructure() {
        // 金叉 vs 死叉
        double lossRateGoldenCross = calculateLossRate(t -> t.getContext().isGoldenCross());
        double lossRateDeathCross = calculateLossRate(t -> t.getContext().isDeathCross());

        // EMA12 vs EMA144 結構
        double lossRateEMA12AboveEMA50 = calculateLossRate(t -> t.getContext().getEma12() > t.getContext().getEma144());
        double lossRateEMA12BelowEMA50 = calculateLossRate(t -> t.getContext().getEma12() < t.getContext().getEma144());

        return EMAStructureAnalysis.builder()
            .lossRateGoldenCross(lossRateGoldenCross)
            .lossRateDeathCross(lossRateDeathCross)
            .lossRateEMA12AboveEMA50(lossRateEMA12AboveEMA50)
            .lossRateEMA12BelowEMA50(lossRateEMA12BelowEMA50)
            .build();
    }

    /**
     * 分析價格與 EMA 的距離
     */
    private DistanceFromEMAAnalysis analyzeDistanceFromEMA() {
        double avgDistFromEMA12Losing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> Math.abs(t.getContext().getDistanceFromEma12Percent()))
            .average()
            .orElse(0);

        double avgDistFromEMA12Winning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> Math.abs(t.getContext().getDistanceFromEma12Percent()))
            .average()
            .orElse(0);

        // 過度延伸的勝率
        double lossRateOverextended = calculateLossRate(t -> Math.abs(t.getContext().getDistanceFromEma12Percent()) > 2.0);
        double lossRateNormal = calculateLossRate(t -> Math.abs(t.getContext().getDistanceFromEma12Percent()) <= 2.0);

        return DistanceFromEMAAnalysis.builder()
            .avgDistFromEMA12Losing(avgDistFromEMA12Losing)
            .avgDistFromEMA12Winning(avgDistFromEMA12Winning)
            .lossRateOverextended(lossRateOverextended)
            .lossRateNormal(lossRateNormal)
            .build();
    }

    /**
     * 分析 K 線形態
     */
    private CandlePatternAnalysis analyzeCandlePatterns() {
        // 陽線 vs 陰線入場
        double lossRateBullishCandle = calculateLossRate(t -> t.getContext().isBullishCandle());
        double lossRateBearishCandle = calculateLossRate(t -> !t.getContext().isBullishCandle());

        // 按實體大小分析
        double avgCandleBodyLosing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getCandleBodyPercent())
            .average()
            .orElse(0);

        double avgCandleBodyWinning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getCandleBodyPercent())
            .average()
            .orElse(0);

        return CandlePatternAnalysis.builder()
            .lossRateBullishCandle(lossRateBullishCandle)
            .lossRateBearishCandle(lossRateBearishCandle)
            .avgCandleBodyLosing(avgCandleBodyLosing)
            .avgCandleBodyWinning(avgCandleBodyWinning)
            .build();
    }

    /**
     * 按規則分析
     */
    private Map<Integer, RuleStats> analyzeByRule() {
        Map<Integer, RuleStats> result = new TreeMap<>();

        for (int rule = 1; rule <= 8; rule++) {
            final int r = rule;

            // Long 規則
            long longTotal = allTrades.stream()
                .filter(t -> t.getDirection() == Trade.Direction.LONG && t.getRuleNumber() == r)
                .count();
            long longLosses = losingTrades.stream()
                .filter(t -> t.getDirection() == Trade.Direction.LONG && t.getRuleNumber() == r)
                .count();

            if (longTotal > 0) {
                double lossRate = longLosses * 100.0 / longTotal;
                double totalPnl = allTrades.stream()
                    .filter(t -> t.getDirection() == Trade.Direction.LONG && t.getRuleNumber() == r)
                    .mapToDouble(Trade::getNetPnl)
                    .sum();

                result.put(rule, RuleStats.builder()
                    .rule(rule)
                    .direction("LONG")
                    .totalTrades((int) longTotal)
                    .losses((int) longLosses)
                    .lossRate(lossRate)
                    .totalPnl(totalPnl)
                    .build());
            }

            // Short 規則
            long shortTotal = allTrades.stream()
                .filter(t -> t.getDirection() == Trade.Direction.SHORT && t.getRuleNumber() == r)
                .count();
            long shortLosses = losingTrades.stream()
                .filter(t -> t.getDirection() == Trade.Direction.SHORT && t.getRuleNumber() == r)
                .count();

            if (shortTotal > 0) {
                double lossRate = shortLosses * 100.0 / shortTotal;
                double totalPnl = allTrades.stream()
                    .filter(t -> t.getDirection() == Trade.Direction.SHORT && t.getRuleNumber() == r)
                    .mapToDouble(Trade::getNetPnl)
                    .sum();

                result.put(rule + 10, RuleStats.builder()  // 用 11-18 表示 short 規則
                    .rule(rule)
                    .direction("SHORT")
                    .totalTrades((int) shortTotal)
                    .losses((int) shortLosses)
                    .lossRate(lossRate)
                    .totalPnl(totalPnl)
                    .build());
            }
        }

        return result;
    }

    /**
     * 分析連續虧損
     */
    private ConsecutiveAnalysis analyzeConsecutive() {
        // 連續虧損後的勝率
        double lossRateAfterConsecutiveLosses = calculateLossRate(t -> t.getContext().getConsecutiveLosses() >= 2);
        double lossRateAfterConsecutiveWins = calculateLossRate(t -> t.getContext().getConsecutiveWins() >= 2);

        return ConsecutiveAnalysis.builder()
            .lossRateAfterConsecutiveLosses(lossRateAfterConsecutiveLosses)
            .lossRateAfterConsecutiveWins(lossRateAfterConsecutiveWins)
            .build();
    }

    /**
     * 分析止損距離
     */
    private StopLossAnalysis analyzeStopLossDistance() {
        double avgSLDistanceLosing = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getStopLossDistancePercent())
            .average()
            .orElse(0);

        double avgSLDistanceWinning = winningTrades.stream()
            .filter(t -> t.getContext() != null)
            .mapToDouble(t -> t.getContext().getStopLossDistancePercent())
            .average()
            .orElse(0);

        // 按止損距離分組
        Map<String, Double> lossRateBySLDistance = new LinkedHashMap<>();
        lossRateBySLDistance.put("<0.5%", calculateLossRate(t -> t.getContext().getStopLossDistancePercent() < 0.5));
        lossRateBySLDistance.put("0.5-1%", calculateLossRate(t -> t.getContext().getStopLossDistancePercent() >= 0.5 && t.getContext().getStopLossDistancePercent() < 1.0));
        lossRateBySLDistance.put("1-2%", calculateLossRate(t -> t.getContext().getStopLossDistancePercent() >= 1.0 && t.getContext().getStopLossDistancePercent() < 2.0));
        lossRateBySLDistance.put("2-3%", calculateLossRate(t -> t.getContext().getStopLossDistancePercent() >= 2.0 && t.getContext().getStopLossDistancePercent() < 3.0));
        lossRateBySLDistance.put(">3%", calculateLossRate(t -> t.getContext().getStopLossDistancePercent() >= 3.0));

        return StopLossAnalysis.builder()
            .avgSLDistanceLosing(avgSLDistanceLosing)
            .avgSLDistanceWinning(avgSLDistanceWinning)
            .lossRateBySLDistance(lossRateBySLDistance)
            .build();
    }

    /**
     * 生成關鍵發現
     */
    private List<String> generateKeyFindings() {
        List<String> findings = new ArrayList<>();

        // 分析最差的時段
        Map<Integer, HourStats> hourStats = analyzeByHour();
        hourStats.entrySet().stream()
            .filter(e -> e.getValue().getTotalTrades() >= 5)  // 至少 5 筆交易
            .max(Comparator.comparingDouble(e -> e.getValue().getLossRate()))
            .ifPresent(e -> findings.add(String.format(
                "WORST HOUR: %02d:00 UTC has %.1f%% loss rate (%d losses / %d trades)",
                e.getKey(), e.getValue().getLossRate(),
                e.getValue().getLosses(), e.getValue().getTotalTrades())));

        // 分析最差的星期
        Map<String, DayStats> dayStats = analyzeByDayOfWeek();
        dayStats.entrySet().stream()
            .filter(e -> e.getValue().getTotalTrades() >= 5)
            .max(Comparator.comparingDouble(e -> e.getValue().getLossRate()))
            .ifPresent(e -> findings.add(String.format(
                "WORST DAY: %s has %.1f%% loss rate (%d losses / %d trades)",
                e.getKey(), e.getValue().getLossRate(),
                e.getValue().getLosses(), e.getValue().getTotalTrades())));

        // ADX 分析
        ADXAnalysis adxAnalysis = analyzeADX();
        if (adxAnalysis.getAvgADXLosing() > adxAnalysis.getAvgADXWinning()) {
            findings.add(String.format(
                "ADX INSIGHT: Losing trades have higher ADX (%.1f) than winning trades (%.1f)",
                adxAnalysis.getAvgADXLosing(), adxAnalysis.getAvgADXWinning()));
        }

        // 成交量分析
        VolumeAnalysis volumeAnalysis = analyzeVolume();
        if (volumeAnalysis.getAvgVolumeRatioLosing() < 0.8) {
            findings.add(String.format(
                "VOLUME WARNING: Losing trades often occur on LOW volume (avg ratio: %.2fx)",
                volumeAnalysis.getAvgVolumeRatioLosing()));
        }
        if (volumeAnalysis.getAvgVolumeRatioLosing() > 1.5) {
            findings.add(String.format(
                "VOLUME WARNING: Losing trades often occur on HIGH volume (avg ratio: %.2fx)",
                volumeAnalysis.getAvgVolumeRatioLosing()));
        }

        // 過度延伸分析
        DistanceFromEMAAnalysis emaDistAnalysis = analyzeDistanceFromEMA();
        if (emaDistAnalysis.getLossRateOverextended() > emaDistAnalysis.getLossRateNormal() + 10) {
            findings.add(String.format(
                "OVEREXTENSION WARNING: Trades far from EMA12 (>2%%) have %.1f%% loss rate vs %.1f%% for normal",
                emaDistAnalysis.getLossRateOverextended(), emaDistAnalysis.getLossRateNormal()));
        }

        // 規則分析 - 找出最差的規則
        Map<Integer, RuleStats> ruleStats = analyzeByRule();
        ruleStats.entrySet().stream()
            .filter(e -> e.getValue().getTotalTrades() >= 3)
            .max(Comparator.comparingDouble(e -> e.getValue().getLossRate()))
            .ifPresent(e -> findings.add(String.format(
                "WORST RULE: %s Rule %d has %.1f%% loss rate (%d losses / %d trades, total PnL: %.2f USDT)",
                e.getValue().getDirection(), e.getValue().getRule(), e.getValue().getLossRate(),
                e.getValue().getLosses(), e.getValue().getTotalTrades(), e.getValue().getTotalPnl())));

        return findings;
    }

    /**
     * 計算滿足條件的虧損率
     */
    private double calculateLossRate(Predicate<Trade> filter) {
        long total = allTrades.stream()
            .filter(t -> t.getContext() != null)
            .filter(filter)
            .count();

        if (total == 0) return 0;

        long losses = losingTrades.stream()
            .filter(t -> t.getContext() != null)
            .filter(filter)
            .count();

        return losses * 100.0 / total;
    }

    /**
     * 輸出分析報告到控制台
     */
    public void printReport(AnalysisReport report) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    LOSING TRADE ANALYSIS REPORT");
        System.out.println("=".repeat(80));

        System.out.printf("\nTotal Trades: %d | Winners: %d | Losers: %d | Win Rate: %.1f%%\n",
            report.getTotalTrades(), report.getWinningTrades(),
            report.getLosingTrades(), report.getWinRate());

        // 關鍵發現
        if (!report.getKeyFindings().isEmpty()) {
            System.out.println("\n" + "-".repeat(40));
            System.out.println("KEY FINDINGS:");
            System.out.println("-".repeat(40));
            for (String finding : report.getKeyFindings()) {
                System.out.println("  * " + finding);
            }
        }

        // 時間分析
        System.out.println("\n" + "-".repeat(40));
        System.out.println("LOSS RATE BY HOUR (UTC):");
        System.out.println("-".repeat(40));
        report.getHourAnalysis().forEach((hour, stats) -> {
            if (stats.getTotalTrades() >= 3) {
                String bar = "█".repeat((int) (stats.getLossRate() / 5));
                System.out.printf("  %02d:00  %5.1f%% %s (%d/%d)\n",
                    hour, stats.getLossRate(), bar, stats.getLosses(), stats.getTotalTrades());
            }
        });

        System.out.println("\n" + "-".repeat(40));
        System.out.println("LOSS RATE BY DAY OF WEEK:");
        System.out.println("-".repeat(40));
        report.getDayOfWeekAnalysis().forEach((day, stats) -> {
            String bar = "█".repeat((int) (stats.getLossRate() / 5));
            System.out.printf("  %-9s  %5.1f%% %s (%d/%d)\n",
                day, stats.getLossRate(), bar, stats.getLosses(), stats.getTotalTrades());
        });

        System.out.println("\n" + "-".repeat(40));
        System.out.println("LOSS RATE BY SESSION:");
        System.out.println("-".repeat(40));
        report.getSessionAnalysis().forEach((session, stats) -> {
            System.out.printf("  %-10s  %5.1f%% loss rate (%d/%d), Total Loss: %.2f USDT\n",
                session, stats.getLossRate(), stats.getLosses(),
                stats.getTotalTrades(), stats.getTotalLoss());
        });

        // ADX 分析
        System.out.println("\n" + "-".repeat(40));
        System.out.println("ADX ANALYSIS:");
        System.out.println("-".repeat(40));
        ADXAnalysis adx = report.getAdxAnalysis();
        System.out.printf("  Avg ADX (Losing):  %.2f\n", adx.getAvgADXLosing());
        System.out.printf("  Avg ADX (Winning): %.2f\n", adx.getAvgADXWinning());
        System.out.println("  Loss Rate by ADX Range:");
        adx.getLossRateByADXRange().forEach((range, rate) ->
            System.out.printf("    %-10s: %5.1f%%\n", range, rate));

        // 成交量分析
        System.out.println("\n" + "-".repeat(40));
        System.out.println("VOLUME ANALYSIS:");
        System.out.println("-".repeat(40));
        VolumeAnalysis vol = report.getVolumeAnalysis();
        System.out.printf("  Avg Volume Ratio (Losing):  %.2fx\n", vol.getAvgVolumeRatioLosing());
        System.out.printf("  Avg Volume Ratio (Winning): %.2fx\n", vol.getAvgVolumeRatioWinning());
        System.out.println("  Loss Rate by Volume:");
        vol.getLossRateByVolumeRange().forEach((range, rate) ->
            System.out.printf("    %-18s: %5.1f%%\n", range, rate));

        // 止損分析
        System.out.println("\n" + "-".repeat(40));
        System.out.println("STOP LOSS DISTANCE ANALYSIS:");
        System.out.println("-".repeat(40));
        StopLossAnalysis sl = report.getStopLossAnalysis();
        System.out.printf("  Avg SL Distance (Losing):  %.2f%%\n", sl.getAvgSLDistanceLosing());
        System.out.printf("  Avg SL Distance (Winning): %.2f%%\n", sl.getAvgSLDistanceWinning());
        System.out.println("  Loss Rate by SL Distance:");
        sl.getLossRateBySLDistance().forEach((range, rate) ->
            System.out.printf("    %-10s: %5.1f%%\n", range, rate));

        // 規則分析
        System.out.println("\n" + "-".repeat(40));
        System.out.println("RULE PERFORMANCE:");
        System.out.println("-".repeat(40));
        report.getRuleAnalysis().forEach((key, stats) -> {
            if (stats.getTotalTrades() > 0) {
                System.out.printf("  %s Rule %d: %5.1f%% loss rate (%d/%d), PnL: %+.2f USDT\n",
                    stats.getDirection(), stats.getRule(), stats.getLossRate(),
                    stats.getLosses(), stats.getTotalTrades(), stats.getTotalPnl());
            }
        });

        System.out.println("\n" + "=".repeat(80));
    }

    /**
     * 輸出報告到文件
     */
    public void saveReportToFile(AnalysisReport report, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("LOSING TRADE ANALYSIS REPORT");
            writer.println("=".repeat(60));
            writer.printf("Total Trades: %d%n", report.getTotalTrades());
            writer.printf("Winning Trades: %d%n", report.getWinningTrades());
            writer.printf("Losing Trades: %d%n", report.getLosingTrades());
            writer.printf("Win Rate: %.2f%%%n", report.getWinRate());
            writer.println();

            writer.println("KEY FINDINGS:");
            writer.println("-".repeat(40));
            for (String finding : report.getKeyFindings()) {
                writer.println("  * " + finding);
            }
            writer.println();

            // 完整數據以 CSV 格式輸出
            writer.println("HOURLY LOSS RATES:");
            writer.println("Hour,TotalTrades,Losses,LossRate%");
            report.getHourAnalysis().forEach((hour, stats) ->
                writer.printf("%d,%d,%d,%.2f%n", hour, stats.getTotalTrades(),
                    stats.getLosses(), stats.getLossRate()));

            log.info("Analysis report saved to: {}", filePath);
        }
    }

    // ==================== Data Classes ====================

    @Data
    @Builder
    public static class AnalysisReport {
        private int totalTrades;
        private int losingTrades;
        private int winningTrades;
        private double winRate;

        private Map<Integer, HourStats> hourAnalysis;
        private Map<String, DayStats> dayOfWeekAnalysis;
        private Map<Integer, MonthStats> monthAnalysis;
        private Map<String, SessionStats> sessionAnalysis;

        private ADXAnalysis adxAnalysis;
        private VolumeAnalysis volumeAnalysis;
        private ATRAnalysis atrAnalysis;
        private EMAStructureAnalysis emaStructureAnalysis;
        private DistanceFromEMAAnalysis distanceFromEmaAnalysis;
        private CandlePatternAnalysis candlePatternAnalysis;
        private Map<Integer, RuleStats> ruleAnalysis;
        private ConsecutiveAnalysis consecutiveAnalysis;
        private StopLossAnalysis stopLossAnalysis;

        private List<String> keyFindings;
    }

    @Data @Builder
    public static class HourStats {
        private int hour;
        private int totalTrades;
        private int losses;
        private double lossRate;
        private double avgLoss;
    }

    @Data @Builder
    public static class DayStats {
        private String dayOfWeek;
        private int totalTrades;
        private int losses;
        private double lossRate;
        private double totalLoss;
    }

    @Data @Builder
    public static class MonthStats {
        private int month;
        private int totalTrades;
        private int losses;
        private double lossRate;
    }

    @Data @Builder
    public static class SessionStats {
        private String session;
        private int totalTrades;
        private int losses;
        private double lossRate;
        private double totalLoss;
    }

    @Data @Builder
    public static class ADXAnalysis {
        private double avgADXLosing;
        private double avgADXWinning;
        private Map<String, Double> lossRateByADXRange;
    }

    @Data @Builder
    public static class VolumeAnalysis {
        private double avgVolumeRatioLosing;
        private double avgVolumeRatioWinning;
        private Map<String, Double> lossRateByVolumeRange;
    }

    @Data @Builder
    public static class ATRAnalysis {
        private double avgATRPercentLosing;
        private double avgATRPercentWinning;
    }

    @Data @Builder
    public static class EMAStructureAnalysis {
        private double lossRateGoldenCross;
        private double lossRateDeathCross;
        private double lossRateEMA12AboveEMA50;
        private double lossRateEMA12BelowEMA50;
    }

    @Data @Builder
    public static class DistanceFromEMAAnalysis {
        private double avgDistFromEMA12Losing;
        private double avgDistFromEMA12Winning;
        private double lossRateOverextended;
        private double lossRateNormal;
    }

    @Data @Builder
    public static class CandlePatternAnalysis {
        private double lossRateBullishCandle;
        private double lossRateBearishCandle;
        private double avgCandleBodyLosing;
        private double avgCandleBodyWinning;
    }

    @Data @Builder
    public static class RuleStats {
        private int rule;
        private String direction;
        private int totalTrades;
        private int losses;
        private double lossRate;
        private double totalPnl;
    }

    @Data @Builder
    public static class ConsecutiveAnalysis {
        private double lossRateAfterConsecutiveLosses;
        private double lossRateAfterConsecutiveWins;
    }

    @Data @Builder
    public static class StopLossAnalysis {
        private double avgSLDistanceLosing;
        private double avgSLDistanceWinning;
        private Map<String, Double> lossRateBySLDistance;
    }
}
