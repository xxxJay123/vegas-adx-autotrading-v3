package com.vegas.backtest.reporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vegas.backtest.model.BacktestResult;
import com.vegas.backtest.model.Trade;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML reports with TradingView dark theme styling.
 */
@Slf4j
public class HtmlReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    private static final DateTimeFormatter MONTH_FORMATTER =
        DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.of("UTC"));

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generate HTML report for backtest result
     * @param result backtest result
     * @param outputPath output file path
     * @return path to generated report
     */
    public Path generateReport(BacktestResult result, String outputPath) throws IOException {
        log.info("Generating HTML report: {}", outputPath);

        String html = buildHtmlReport(result);

        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, html);

        log.info("Report generated successfully: {}", path.toAbsolutePath());

        return path;
    }

    /**
     * Build complete HTML report
     */
    private String buildHtmlReport(BacktestResult result) {
        String equityChartData = buildEquityChartData(result);
        String drawdownChartData = buildDrawdownChartData(result);
        String monthlyHeatmapData = buildMonthlyHeatmapData(result);
        String summaryMetrics = buildSummaryMetrics(result);
        String tradesTable = buildTradesTable(result);

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Backtest Report - %s %d</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://cdn.plot.ly/plotly-2.27.0.min.js"></script>
    <style>
        body {
            background-color: #131722;
            color: #d1d4dc;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
        }
        .tv-card {
            background-color: #1e222d;
            border: 1px solid #2a2e39;
            border-radius: 8px;
        }
        .tv-table {
            background-color: #1e222d;
        }
        .tv-table th {
            background-color: #2a2e39;
            color: #787b86;
            font-weight: 600;
            text-transform: uppercase;
            font-size: 11px;
            letter-spacing: 0.5px;
        }
        .tv-table td {
            border-bottom: 1px solid #2a2e39;
        }
        .tv-table tr:hover {
            background-color: #2a2e39;
        }
        .metric-label {
            color: #787b86;
            font-size: 12px;
            font-weight: 500;
        }
        .metric-value {
            color: #d1d4dc;
            font-size: 20px;
            font-weight: 600;
        }
        .positive {
            color: #089981;
        }
        .negative {
            color: #f23645;
        }
        .neutral {
            color: #787b86;
        }
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        ::-webkit-scrollbar-track {
            background: #1e222d;
        }
        ::-webkit-scrollbar-thumb {
            background: #2a2e39;
            border-radius: 4px;
        }
        ::-webkit-scrollbar-thumb:hover {
            background: #363a45;
        }
    </style>
</head>
<body class="p-6">
    <div class="max-w-7xl mx-auto space-y-6">
        <!-- Header -->
        <div class="tv-card p-6">
            <h1 class="text-3xl font-bold mb-2">%s %d Backtest Report</h1>
            <p class="text-sm text-gray-400">Vegas ADX Strategy - 16 Rules System</p>
        </div>

        <!-- Summary Metrics -->
        <div class="tv-card p-6">
            <h2 class="text-xl font-semibold mb-4">Performance Summary</h2>
            <div class="grid grid-cols-2 md:grid-cols-4 gap-6">
                %s
            </div>
        </div>

        <!-- Equity Curve -->
        <div class="tv-card p-6">
            <h2 class="text-xl font-semibold mb-4">Equity Curve</h2>
            <div id="equityChart" style="width:100%%; height:400px;"></div>
        </div>

        <!-- Drawdown Chart -->
        <div class="tv-card p-6">
            <h2 class="text-xl font-semibold mb-4">Underwater Drawdown</h2>
            <div id="drawdownChart" style="width:100%%; height:300px;"></div>
        </div>

        <!-- Monthly Returns Heatmap -->
        <div class="tv-card p-6">
            <h2 class="text-xl font-semibold mb-4">Monthly Returns (%%)</h2>
            <div id="monthlyHeatmap" style="width:100%%; height:300px;"></div>
        </div>

        <!-- Trades Table -->
        <div class="tv-card p-6">
            <h2 class="text-xl font-semibold mb-4">Trade History (%d trades)</h2>
            <div class="overflow-x-auto">
                <table class="tv-table w-full text-sm">
                    <thead>
                        <tr>
                            <th class="px-4 py-2 text-left">#</th>
                            <th class="px-4 py-2 text-left">Type</th>
                            <th class="px-4 py-2 text-left">Rule</th>
                            <th class="px-4 py-2 text-left">Entry Time</th>
                            <th class="px-4 py-2 text-right">Entry Price</th>
                            <th class="px-4 py-2 text-left">Exit Time</th>
                            <th class="px-4 py-2 text-right">Exit Price</th>
                            <th class="px-4 py-2 text-left">Exit</th>
                            <th class="px-4 py-2 text-right">PnL USDT</th>
                            <th class="px-4 py-2 text-right">PnL %%</th>
                            <th class="px-4 py-2 text-right">Fees</th>
                            <th class="px-4 py-2 text-right">Hold (h)</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Footer -->
        <div class="text-center text-sm text-gray-500 py-4">
            <p>Generated by Vegas ADX Backtesting System</p>
            <p>© 2025 Production-Grade Backtesting Engine</p>
        </div>
    </div>

    <script>
        // Equity Chart
        %s

        // Drawdown Chart
        %s

        // Monthly Heatmap
        %s
    </script>
</body>
</html>
""".formatted(
            result.getSymbol(), result.getYear(),
            result.getSymbol(), result.getYear(),
            summaryMetrics,
            result.getTotalTrades(),
            tradesTable,
            equityChartData,
            drawdownChartData,
            monthlyHeatmapData
        );
    }

    /**
     * Build summary metrics HTML
     */
    private String buildSummaryMetrics(BacktestResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildMetric("Net Profit",
            String.format("$%.2f", result.getNetProfit()),
            result.getNetProfit() >= 0));

        sb.append(buildMetric("Return %",
            String.format("%.2f%%", result.getNetProfitPercent()),
            result.getNetProfitPercent() >= 0));

        sb.append(buildMetric("Max Drawdown",
            String.format("%.2f%%", result.getMaxDrawdown()),
            false));

        sb.append(buildMetric("Win Rate",
            String.format("%.1f%%", result.getWinRate()),
            result.getWinRate() >= 50));

        sb.append(buildMetric("Total Trades",
            String.valueOf(result.getTotalTrades()),
            true));

        sb.append(buildMetric("Winners",
            String.valueOf(result.getWinningTrades()),
            true));

        sb.append(buildMetric("Losers",
            String.valueOf(result.getLosingTrades()),
            true));

        sb.append(buildMetric("Profit Factor",
            String.format("%.2f", result.getProfitFactor()),
            result.getProfitFactor() >= 1));

        sb.append(buildMetric("Sharpe Ratio",
            String.format("%.2f", result.getSharpeRatio()),
            result.getSharpeRatio() >= 1));

        sb.append(buildMetric("Avg Win",
            String.format("$%.2f", result.getAverageWin()),
            true));

        sb.append(buildMetric("Avg Loss",
            String.format("$%.2f", result.getAverageLoss()),
            false));

        sb.append(buildMetric("Total Fees",
            String.format("$%.2f", result.getTotalFees()),
            false));

        return sb.toString();
    }

    /**
     * Build single metric HTML
     */
    private String buildMetric(String label, String value, boolean positive) {
        String colorClass = positive ? "positive" : "negative";
        if (label.equals("Total Trades") || label.equals("Winners") || label.equals("Losers")) {
            colorClass = "neutral";
        }

        return String.format("""
                <div>
                    <div class="metric-label">%s</div>
                    <div class="metric-value %s">%s</div>
                </div>
                """, label, colorClass, value);
    }

    /**
     * Build equity chart Plotly data
     */
    private String buildEquityChartData(BacktestResult result) {
        List<String> timestamps = new ArrayList<>();
        List<Double> balances = new ArrayList<>();

        for (BacktestResult.EquityPoint point : result.getEquityCurve()) {
            timestamps.add(Instant.ofEpochMilli(point.getTimestamp())
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            balances.add(point.getBalance());
        }

        return String.format("""
            var equityData = [{
                x: %s,
                y: %s,
                type: 'scatter',
                mode: 'lines',
                line: { color: '#089981', width: 2 },
                fill: 'tozeroy',
                fillcolor: 'rgba(8, 153, 129, 0.1)'
            }];
            var equityLayout = {
                paper_bgcolor: '#1e222d',
                plot_bgcolor: '#1e222d',
                font: { color: '#d1d4dc' },
                xaxis: { gridcolor: '#2a2e39', showgrid: true },
                yaxis: { gridcolor: '#2a2e39', showgrid: true, title: 'Balance (USDT)' },
                margin: { l: 60, r: 20, t: 20, b: 60 }
            };
            Plotly.newPlot('equityChart', equityData, equityLayout, {responsive: true, displayModeBar: false});
            """, gson.toJson(timestamps), gson.toJson(balances));
    }

    /**
     * Build drawdown chart Plotly data
     */
    private String buildDrawdownChartData(BacktestResult result) {
        List<String> timestamps = new ArrayList<>();
        List<Double> drawdowns = new ArrayList<>();

        double peak = result.getInitialBalance();

        for (BacktestResult.EquityPoint point : result.getEquityCurve()) {
            if (point.getBalance() > peak) {
                peak = point.getBalance();
            }
            double dd = (peak - point.getBalance()) / peak * 100.0;

            timestamps.add(Instant.ofEpochMilli(point.getTimestamp())
                .atZone(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            drawdowns.add(-dd);
        }

        return String.format("""
            var drawdownData = [{
                x: %s,
                y: %s,
                type: 'scatter',
                mode: 'lines',
                line: { color: '#f23645', width: 2 },
                fill: 'tozeroy',
                fillcolor: 'rgba(242, 54, 69, 0.2)'
            }];
            var drawdownLayout = {
                paper_bgcolor: '#1e222d',
                plot_bgcolor: '#1e222d',
                font: { color: '#d1d4dc' },
                xaxis: { gridcolor: '#2a2e39', showgrid: true },
                yaxis: { gridcolor: '#2a2e39', showgrid: true, title: 'Drawdown (%%)', ticksuffix: '%%' },
                margin: { l: 60, r: 20, t: 20, b: 60 }
            };
            Plotly.newPlot('drawdownChart', drawdownData, drawdownLayout, {responsive: true, displayModeBar: false});
            """, gson.toJson(timestamps), gson.toJson(drawdowns));
    }

    /**
     * Build monthly heatmap data
     */
    private String buildMonthlyHeatmapData(BacktestResult result) {
        Map<String, Map<String, Double>> yearMonthReturns = new HashMap<>();

        for (BacktestResult.MonthlyReturn mr : result.getMonthlyReturns()) {
            String[] parts = mr.getMonth().split("-");
            String year = parts[0];
            String month = parts[1];

            yearMonthReturns.computeIfAbsent(year, k -> new HashMap<>())
                .put(month, mr.getReturnPercent());
        }

        List<String> months = List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12");
        List<String> monthLabels = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");

        List<List<Double>> zValues = new ArrayList<>();
        List<String> yLabels = new ArrayList<>(yearMonthReturns.keySet());
        yLabels.sort(String::compareTo);

        for (String year : yLabels) {
            List<Double> row = new ArrayList<>();
            Map<String, Double> monthReturns = yearMonthReturns.get(year);
            for (String month : months) {
                row.add(monthReturns.getOrDefault(month, 0.0));
            }
            zValues.add(row);
        }

        return String.format("""
            var heatmapData = [{
                z: %s,
                x: %s,
                y: %s,
                type: 'heatmap',
                colorscale: [
                    [0, '#f23645'],
                    [0.5, '#2a2e39'],
                    [1, '#089981']
                ],
                zmid: 0,
                text: %s,
                texttemplate: '%%{z:.1f}%%',
                textfont: { size: 10 },
                hovertemplate: '%%{y}-%%{x}: %%{z:.2f}%%<extra></extra>'
            }];
            var heatmapLayout = {
                paper_bgcolor: '#1e222d',
                plot_bgcolor: '#1e222d',
                font: { color: '#d1d4dc' },
                xaxis: { side: 'top' },
                yaxis: { autorange: 'reversed' },
                margin: { l: 60, r: 20, t: 60, b: 20 }
            };
            Plotly.newPlot('monthlyHeatmap', heatmapData, heatmapLayout, {responsive: true, displayModeBar: false});
            """,
            gson.toJson(zValues),
            gson.toJson(monthLabels),
            gson.toJson(yLabels),
            gson.toJson(zValues)
        );
    }

    /**
     * Build trades table HTML
     */
    private String buildTradesTable(BacktestResult result) {
        StringBuilder sb = new StringBuilder();

        for (Trade trade : result.getTrades()) {
            String typeColor = trade.getDirection() == Trade.Direction.LONG ? "text-blue-400" : "text-purple-400";
            String pnlColor = trade.isWinner() ? "positive" : "negative";

            String entryTime = Instant.ofEpochMilli(trade.getEntryTime())
                .atZone(ZoneId.of("UTC"))
                .format(DATE_FORMATTER);

            String exitTime = Instant.ofEpochMilli(trade.getExitTime())
                .atZone(ZoneId.of("UTC"))
                .format(DATE_FORMATTER);

            sb.append(String.format("""
                    <tr>
                        <td class="px-4 py-2">%d</td>
                        <td class="px-4 py-2 %s font-semibold">%s</td>
                        <td class="px-4 py-2">R%d</td>
                        <td class="px-4 py-2">%s</td>
                        <td class="px-4 py-2 text-right">%.2f</td>
                        <td class="px-4 py-2">%s</td>
                        <td class="px-4 py-2 text-right">%.2f</td>
                        <td class="px-4 py-2">%s</td>
                        <td class="px-4 py-2 text-right %s">%.2f</td>
                        <td class="px-4 py-2 text-right %s">%.2f%%</td>
                        <td class="px-4 py-2 text-right">%.2f</td>
                        <td class="px-4 py-2 text-right">%.1f</td>
                    </tr>
                    """,
                trade.getId(),
                typeColor,
                trade.getDirection(),
                trade.getRuleNumber(),
                entryTime,
                trade.getEntryPrice(),
                exitTime,
                trade.getExitPrice(),
                trade.getExitReason() == Trade.ExitReason.TAKE_PROFIT ? "TP" : "SL",
                pnlColor,
                trade.getNetPnl(),
                pnlColor,
                trade.getPnlPercent(),
                trade.getFees(),
                trade.getHoldTimeHours()
            ));
        }

        return sb.toString();
    }
}
