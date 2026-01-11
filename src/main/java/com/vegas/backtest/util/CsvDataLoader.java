package com.vegas.backtest.util;

import com.vegas.backtest.model.Candle;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads candle data from CSV files.
 * Supports formats:
 *   - Old: timestamp,open,high,low,close,volume
 *   - New: timestamp,datetime_utc,open,high,low,close,volume
 * Timestamp can be milliseconds or ISO format.
 */
@Slf4j
public class CsvDataLoader {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Load candles from CSV file
     * @param filePath path to CSV file
     * @return list of candles
     */
    public List<Candle> loadCandles(String filePath) throws IOException {
        log.info("Loading candles from: {}", filePath);

        List<Candle> candles = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(Paths.get(filePath));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                 .builder()
                 .setHeader()
                 .setSkipHeaderRecord(true)
                 .setIgnoreHeaderCase(true)
                 .setTrim(true)
                 .build())) {

            for (CSVRecord record : csvParser) {
                try {
                    // Support both old format (timestamp,open,high,low,close,volume)
                    // and new format (timestamp,datetime_utc,open,high,low,close,volume)
                    long timestamp = parseTimestamp(record.get("timestamp"));
                    double open = Double.parseDouble(record.get("open"));
                    double high = Double.parseDouble(record.get("high"));
                    double low = Double.parseDouble(record.get("low"));
                    double close = Double.parseDouble(record.get("close"));
                    double volume = Double.parseDouble(record.get("volume"));

                    Candle candle = Candle.builder()
                        .timestamp(timestamp)
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .volume(volume)
                        .build();

                    candles.add(candle);

                } catch (Exception e) {
                    log.warn("Failed to parse record at line {}: {}", record.getRecordNumber(), e.getMessage());
                }
            }
        }

        candles.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        log.info("Loaded {} candles from {}", candles.size(), filePath);

        return candles;
    }

    /**
     * Parse timestamp from string (supports milliseconds, ISO format, or yyyy-MM-dd HH:mm:ss format)
     * @param timestampStr timestamp string
     * @return timestamp in milliseconds
     */
    private long parseTimestamp(String timestampStr) {
        try {
            return Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            try {
                Instant instant = Instant.parse(timestampStr);
                return instant.toEpochMilli();
            } catch (Exception ex) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(timestampStr, ISO_FORMATTER);
                    return ldt.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
                } catch (Exception ex2) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(timestampStr, DATETIME_FORMATTER);
                        return ldt.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
                    } catch (Exception ex3) {
                        log.error("Failed to parse timestamp: {}", timestampStr);
                        throw new IllegalArgumentException("Invalid timestamp format: " + timestampStr);
                    }
                }
            }
        }
    }
}
