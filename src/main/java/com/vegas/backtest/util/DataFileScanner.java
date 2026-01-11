package com.vegas.backtest.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans data directory for kline CSV files.
 * Supports pattern: kline_SYMBOL.P_YEAR.csv
 */
@Slf4j
public class DataFileScanner {

    // Pattern: kline_SYMBOL.P_YEAR_TIMEFRAME.csv (e.g., kline_BTCUSDT.P_2024_5m.csv)
    // Also supports legacy: kline_SYMBOL.P_YEAR.csv (defaults to 5m)
    private static final Pattern FILENAME_PATTERN = Pattern.compile("kline_([A-Z0-9]+\\.P)_(\\d{4})(?:_([a-zA-Z0-9]+))?\\.csv");

    /**
     * Scan directory for kline files
     * @param dataDir data directory path
     * @return list of discovered data files
     */
    public List<DataFileInfo> scanDataDirectory(String dataDir) throws IOException {
        Path dirPath = Paths.get(dataDir);

        if (!Files.exists(dirPath)) {
            log.warn("Data directory does not exist: {}", dataDir);
            return new ArrayList<>();
        }

        if (!Files.isDirectory(dirPath)) {
            log.error("Path is not a directory: {}", dataDir);
            return new ArrayList<>();
        }

        List<DataFileInfo> dataFiles = new ArrayList<>();

        try (Stream<Path> files = Files.list(dirPath)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".csv"))
                .forEach(path -> {
                    String filename = path.getFileName().toString();
                    Matcher matcher = FILENAME_PATTERN.matcher(filename);

                    if (matcher.matches()) {
                        String symbol = matcher.group(1);
                        int year = Integer.parseInt(matcher.group(2));
                        String timeframe = matcher.group(3);
                        if (timeframe == null || timeframe.isEmpty()) {
                            timeframe = "5m"; // default for legacy files
                        }

                        DataFileInfo info = DataFileInfo.builder()
                            .filePath(path.toString())
                            .symbol(symbol)
                            .year(year)
                            .timeframe(timeframe)
                            .build();

                        dataFiles.add(info);
                        log.debug("Found data file: {} - {} year {} timeframe {}",
                            filename, symbol, year, timeframe);
                    } else {
                        log.debug("Skipping non-matching file: {}", filename);
                    }
                });
        }

        dataFiles.sort((a, b) -> {
            int symbolCompare = a.getSymbol().compareTo(b.getSymbol());
            if (symbolCompare != 0) return symbolCompare;
            int timeframeCompare = a.getTimeframe().compareTo(b.getTimeframe());
            if (timeframeCompare != 0) return timeframeCompare;
            return Integer.compare(a.getYear(), b.getYear());
        });

        log.info("Discovered {} data files in {}", dataFiles.size(), dataDir);

        return dataFiles;
    }

    /**
     * Information about a discovered data file
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataFileInfo {
        private String filePath;
        private String symbol;
        private int year;
        private String timeframe;

        @Override
        public String toString() {
            return String.format("%s - %d - %s (%s)", symbol, year, timeframe, filePath);
        }
    }
}
