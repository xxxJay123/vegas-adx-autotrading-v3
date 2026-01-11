package com.vegas.backtest;

import com.vegas.backtest.analyzer.LosingTradeAnalyzer;
import com.vegas.backtest.config.Config;
import com.vegas.backtest.engine.BacktestEngine;
import com.vegas.backtest.model.BacktestResult;
import com.vegas.backtest.model.Candle;
import com.vegas.backtest.model.Trade;
import com.vegas.backtest.reporter.HtmlReportGenerator;
import com.vegas.backtest.util.BinanceDataLoader;
import com.vegas.backtest.util.CsvDataLoader;
import com.vegas.backtest.util.DataFileScanner;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for the Vegas ADX Backtesting System.
 * Auto-discovers data files and runs backtests year by year.
 */
@Slf4j
public class Main {

    private static final double INITIAL_BALANCE = 10000.0;

    public static void main(String[] args) {
        log.info("========================================");
        log.info("  VEGAS ADX BACKTESTING SYSTEM v1.0");
        log.info("========================================");

        try {
            Config config = Config.getInstance();

            List<DataFileScanner.DataFileInfo> dataFiles = new ArrayList<>();

            // Determine data source and load data accordingly
            if ("binance".equalsIgnoreCase(config.getDataSource())) {
                log.info("Using Binance API as data source");
                dataFiles = prepareDataFromBinance(config);
            } else {
                log.info("Using CSV files as data source");
                DataFileScanner scanner = new DataFileScanner();
                dataFiles = scanner.scanDataDirectory(config.getDataDir());
            }

            if (dataFiles.isEmpty()) {
                log.error("No data available to process");
                if ("csv".equalsIgnoreCase(config.getDataSource())) {
                    log.info("Expected file pattern: kline_SYMBOL.P_YEAR_TIMEFRAME.csv");
                    log.info("Example: kline_BTCUSDT.P_2024_5m.csv or kline_BTCUSDT.P_2024.csv (defaults to 5m)");
                }
                return;
            }

            log.info("Found {} data file(s) to process", dataFiles.size());
            System.out.println("\n╔═══════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                         BACKTEST EXECUTION PLAN                               ║");
            System.out.println("╠═══════════════════════════════════════════════════════════════════════════════╣");

            for (int i = 0; i < dataFiles.size(); i++) {
                DataFileScanner.DataFileInfo fileInfo = dataFiles.get(i);
                System.out.printf("║ %2d. %-74s ║%n", i + 1, fileInfo);
            }
            System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝\n");

            CsvDataLoader dataLoader = new CsvDataLoader();
            BacktestEngine engine = new BacktestEngine(config);
            HtmlReportGenerator reportGenerator = new HtmlReportGenerator();

            Path lastReportPath = null;
            List<Trade> allTrades = new ArrayList<>();  // 收集所有交易用於分析

            System.out.println("\n╔═══════════════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                            BACKTEST RESULTS                                   ║");
            System.out.println("╠═══════╦═══════╦════════════╦════════════╦═══════╦═══════╦════════╦═══════════╣");
            System.out.println("║ Year  ║ Symbl ║  Net P/L   ║  Return %  ║ Trades║  Win% ║ Max DD ║  Sharpe   ║");
            System.out.println("╠═══════╬═══════╬════════════╬════════════╬═══════╬═══════╬════════╬═══════════╣");

            for (DataFileScanner.DataFileInfo fileInfo : dataFiles) {
                try {
                    List<Candle> candles = dataLoader.loadCandles(fileInfo.getFilePath());

                    if (candles.isEmpty()) {
                        log.warn("No candles loaded from: {}", fileInfo.getFilePath());
                        continue;
                    }

                    BacktestResult result = engine.runBacktest(
                        fileInfo.getSymbol(),
                        fileInfo.getYear(),
                        candles,
                        INITIAL_BALANCE
                    );

                    String reportFilename = String.format("report_%s_%d.html",
                        fileInfo.getSymbol(), fileInfo.getYear());
                    String reportPath = config.getReportsDir() + "/" + reportFilename;

                    lastReportPath = reportGenerator.generateReport(result, reportPath);

                    // 收集交易用於分析
                    allTrades.addAll(result.getTrades());

                    printResultRow(result);

                } catch (Exception e) {
                    log.error("Failed to process file: {}", fileInfo.getFilePath(), e);
                    System.out.printf("║ %5d ║ %-5s ║   ERROR    ║            ║       ║       ║        ║           ║%n",
                        fileInfo.getYear(), truncate(fileInfo.getSymbol(), 5));
                }
            }

            System.out.println("╚═══════╩═══════╩════════════╩════════════╩═══════╩═══════╩════════╩═══════════╝");

            // 執行虧損交易分析
            if (!allTrades.isEmpty()) {
                System.out.println("\n");
                LosingTradeAnalyzer analyzer = new LosingTradeAnalyzer(allTrades);
                LosingTradeAnalyzer.AnalysisReport analysisReport = analyzer.analyze();
                analyzer.printReport(analysisReport);

                // 保存分析報告到文件
                try {
                    String analysisPath = config.getReportsDir() + "/losing_trade_analysis.txt";
                    analyzer.saveReportToFile(analysisReport, analysisPath);
                    log.info("Losing trade analysis saved to: {}", analysisPath);
                } catch (IOException e) {
                    log.warn("Failed to save analysis report: {}", e.getMessage());
                }
            }

            log.info("\n========================================");
            log.info("  ALL BACKTESTS COMPLETED");
            log.info("========================================");
            log.info("Reports directory: {}", config.getReportsDir());

            if (config.isAutoOpenReports() && lastReportPath != null) {
                openReportInBrowser(lastReportPath);
            }

        } catch (Exception e) {
            log.error("Fatal error in main execution", e);
            System.exit(1);
        }
    }

    /**
     * Print result row in table format
     */
    private static void printResultRow(BacktestResult result) {
        String netPnl = String.format("%+.2f", result.getNetProfit());
        String returnPct = String.format("%+.2f%%", result.getNetProfitPercent());
        String trades = String.valueOf(result.getTotalTrades());
        String winRate = String.format("%.1f%%", result.getWinRate());
        String maxDD = String.format("%.2f%%", result.getMaxDrawdown());
        String sharpe = String.format("%.2f", result.getSharpeRatio());

        String pnlColor = result.getNetProfit() >= 0 ? "\u001B[32m" : "\u001B[31m";
        String resetColor = "\u001B[0m";

        System.out.printf("║ %5d ║ %-5s ║ %s%10s%s ║ %s%10s%s ║ %5s ║ %5s ║ %6s ║ %9s ║%n",
            result.getYear(),
            truncate(result.getSymbol(), 5),
            pnlColor, netPnl, resetColor,
            pnlColor, returnPct, resetColor,
            trades,
            winRate,
            maxDD,
            sharpe
        );
    }

    /**
     * Truncate string to max length
     */
    private static String truncate(String str, int maxLen) {
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen);
    }

    /**
     * Open HTML report in default browser
     */
    private static void openReportInBrowser(Path reportPath) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(reportPath.toUri());
                log.info("Opened report in browser: {}", reportPath.getFileName());
            } else {
                log.warn("Desktop browsing not supported. Report saved at: {}", reportPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("Failed to open report in browser: {}", e.getMessage());
            log.info("Report saved at: {}", reportPath.toAbsolutePath());
        }
    }

    /**
     * Prepare data from Binance API
     * Downloads data for each timeframe and saves to CSV files
     * @param config configuration
     * @return list of data file info
     */
    private static List<DataFileScanner.DataFileInfo> prepareDataFromBinance(Config config) {
        List<DataFileScanner.DataFileInfo> dataFiles = new ArrayList<>();

        try {
            BinanceDataLoader binanceLoader = new BinanceDataLoader(
                config.getBinanceApiKey(),
                config.getBinanceSecretKey()
            );

            // Test connection first
            if (!binanceLoader.testConnection()) {
                log.error("Failed to connect to Binance API");
                return dataFiles;
            }

            // Ensure data directory exists
            Path dataDir = Paths.get(config.getDataDir());
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("Created data directory: {}", dataDir);
            }

            String symbol = config.getBinanceSymbol();
            String startDate = config.getBinanceStartDate();
            String endDate = config.getBinanceEndDate();

            // Extract year range from start and end dates
            int startYear = extractYear(startDate);
            int endYear = extractYear(endDate);

            // Download data for each timeframe
            for (String timeframe : config.getTimeframes()) {
                log.info("Checking {} data for timeframe: {}", symbol, timeframe);

                try {
                    // Check which years are missing
                    List<Integer> missingYears = new ArrayList<>();
                    for (int year = startYear; year <= endYear; year++) {
                        String filename = String.format("kline_%s_%d_%s.csv",
                            symbol, year, timeframe);
                        Path csvPath = dataDir.resolve(filename);

                        if (Files.exists(csvPath)) {
                            log.info("Found existing file: {}", filename);
                            // Add existing file to dataFiles list
                            DataFileScanner.DataFileInfo fileInfo = DataFileScanner.DataFileInfo.builder()
                                .filePath(csvPath.toString())
                                .symbol(symbol)
                                .year(year)
                                .timeframe(timeframe)
                                .build();
                            dataFiles.add(fileInfo);
                        } else {
                            missingYears.add(year);
                        }
                    }

                    // Skip download if all files exist
                    if (missingYears.isEmpty()) {
                        log.info("All files for {}/{} already exist, skipping download", symbol, timeframe);
                        continue;
                    }

                    log.info("Missing data for years: {}, downloading...", missingYears);

                    // Download all data at once
                    List<Candle> allCandles = binanceLoader.downloadKlines(
                        symbol, timeframe, startDate, endDate
                    );

                    if (allCandles.isEmpty()) {
                        log.warn("No data downloaded for {}/{}", symbol, timeframe);
                        continue;
                    }

                    log.info("Downloaded {} total candles, splitting by year...", allCandles.size());

                    // Split candles by year and save only missing files
                    for (int year : missingYears) {
                        List<Candle> yearCandles = filterCandlesByYear(allCandles, year);

                        if (yearCandles.isEmpty()) {
                            log.debug("No candles found for year {}", year);
                            continue;
                        }

                        // Save to CSV file for this year
                        String filename = String.format("kline_%s_%d_%s.csv",
                            symbol, year, timeframe);
                        Path csvPath = dataDir.resolve(filename);

                        saveCandlesToCsv(yearCandles, csvPath.toString());
                        log.info("Saved {} candles to {}", yearCandles.size(), filename);

                        // Create DataFileInfo for this file
                        DataFileScanner.DataFileInfo fileInfo = DataFileScanner.DataFileInfo.builder()
                            .filePath(csvPath.toString())
                            .symbol(symbol)
                            .year(year)
                            .timeframe(timeframe)
                            .build();

                        dataFiles.add(fileInfo);
                    }

                } catch (Exception e) {
                    log.error("Failed to download data for {}/{}: {}",
                        symbol, timeframe, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error preparing data from Binance: {}", e.getMessage(), e);
        }

        return dataFiles;
    }

    /**
     * Extract year from date string (YYYY-MM-DD format)
     * @param dateStr date string
     * @return year
     */
    private static int extractYear(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return java.time.Year.now().getValue();
        }
        try {
            return Integer.parseInt(dateStr.substring(0, 4));
        } catch (Exception e) {
            return java.time.Year.now().getValue();
        }
    }

    /**
     * Filter candles by year
     * @param candles list of all candles
     * @param year target year
     * @return candles for the specified year
     */
    private static List<Candle> filterCandlesByYear(List<Candle> candles, int year) {
        List<Candle> filtered = new ArrayList<>();

        java.time.ZoneId utcZone = java.time.ZoneId.of("UTC");

        for (Candle candle : candles) {
            java.time.Instant instant = java.time.Instant.ofEpochMilli(candle.getTimestamp());
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, utcZone);

            if (dateTime.getYear() == year) {
                filtered.add(candle);
            }
        }

        return filtered;
    }

    /**
     * Save candles to CSV file
     * @param candles list of candles
     * @param filePath output file path
     */
    private static void saveCandlesToCsv(List<Candle> candles, String filePath) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,datetime_utc,open,high,low,close,volume\n");

        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.of("UTC"));

        for (Candle candle : candles) {
            // Convert timestamp to UTC datetime string
            java.time.Instant instant = java.time.Instant.ofEpochMilli(candle.getTimestamp());
            String datetimeUtc = formatter.format(instant);

            csv.append(String.format("%d,%s,%.8f,%.8f,%.8f,%.8f,%.8f\n",
                candle.getTimestamp(),
                datetimeUtc,
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume()
            ));
        }

        Files.write(Paths.get(filePath), csv.toString().getBytes());
    }
}
