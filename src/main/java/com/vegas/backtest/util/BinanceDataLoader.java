package com.vegas.backtest.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.vegas.backtest.model.Candle;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Downloads Kline (candlestick) data from Binance API.
 * Handles rate limiting, error handling, and data pagination.
 */
@Slf4j
public class BinanceDataLoader {

    private static final String BASE_URL = "https://fapi.binance.com";
    private static final int MAX_LIMIT = 1000; // Binance max klines per request
    private static final long RATE_LIMIT_DELAY_MS = 250; // 250ms between requests to avoid rate limits

    private final OkHttpClient client;
    private final String apiKey;
    private final String secretKey;

    /**
     * Constructor for Binance data loader
     * @param apiKey Binance API key (can be null for public endpoints)
     * @param secretKey Binance secret key (can be null for public endpoints)
     */
    public BinanceDataLoader(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("Binance client initialized with API credentials");
        } else {
            log.info("Binance client initialized without credentials (public endpoints only)");
        }
    }

    /**
     * Download kline data from Binance
     * @param symbol trading symbol (e.g., "BTCUSDT")
     * @param interval timeframe interval (e.g., "5m", "15m", "1h")
     * @param startTime start time in milliseconds (optional, can be null)
     * @param endTime end time in milliseconds (optional, can be null)
     * @return list of candles
     */
    public List<Candle> downloadKlines(String symbol, String interval, Long startTime, Long endTime) {
        log.info("Downloading klines from Binance: symbol={}, interval={}, startTime={}, endTime={}",
            symbol, interval, startTime, endTime);

        List<Candle> allCandles = new ArrayList<>();

        try {
            Long currentStartTime = startTime;
            boolean hasMoreData = true;

            // Estimate total requests for progress tracking
            long totalTimeRange = endTime - startTime;
            long intervalMs = getIntervalInMilliseconds(interval);
            long estimatedCandles = totalTimeRange / intervalMs;
            long estimatedRequests = (estimatedCandles / MAX_LIMIT) + 1;

            int requestCount = 0;
            long lastProgressUpdate = System.currentTimeMillis();

            while (hasMoreData) {
                requestCount++;

                // Show progress every request
                long now = System.currentTimeMillis();
                if (now - lastProgressUpdate >= 1000 || requestCount == 1) {
                    int percentage = (int) Math.min(100, (requestCount * 100.0 / estimatedRequests));
                    String progressBar = createProgressBar(percentage, 50);
                    System.out.print(String.format("\r[%s] %d%% - Downloaded %d candles (%d/%d requests)",
                        progressBar, percentage, allCandles.size(), requestCount, estimatedRequests));
                    System.out.flush();
                    lastProgressUpdate = now;
                }

                StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/fapi/v1/klines?");
                urlBuilder.append("symbol=").append(symbol);
                urlBuilder.append("&interval=").append(interval);
                urlBuilder.append("&limit=").append(MAX_LIMIT);

                if (currentStartTime != null) {
                    urlBuilder.append("&startTime=").append(currentStartTime);
                }
                if (endTime != null) {
                    urlBuilder.append("&endTime=").append(endTime);
                }

                String url = urlBuilder.toString();

                Request.Builder requestBuilder = new Request.Builder().url(url);
                if (apiKey != null && !apiKey.isEmpty()) {
                    requestBuilder.addHeader("X-MBX-APIKEY", apiKey);
                }

                Request request = requestBuilder.build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        log.error("Binance API error: {} - {}", response.code(), errorBody);
                        throw new RuntimeException("Binance API request failed: " + response.code() + " - " + errorBody);
                    }

                    String responseBody = response.body().string();
                    JsonArray klines = JsonParser.parseString(responseBody).getAsJsonArray();

                    if (klines.size() == 0) {
                        hasMoreData = false;
                        break;
                    }

                    for (int i = 0; i < klines.size(); i++) {
                        JsonArray kline = klines.get(i).getAsJsonArray();

                        long timestamp = kline.get(0).getAsLong();
                        double open = kline.get(1).getAsDouble();
                        double high = kline.get(2).getAsDouble();
                        double low = kline.get(3).getAsDouble();
                        double close = kline.get(4).getAsDouble();
                        double volume = kline.get(5).getAsDouble();

                        Candle candle = Candle.builder()
                            .timestamp(timestamp)
                            .open(open)
                            .high(high)
                            .low(low)
                            .close(close)
                            .volume(volume)
                            .build();

                        allCandles.add(candle);
                    }

                    // If we got less than MAX_LIMIT, we've reached the end
                    if (klines.size() < MAX_LIMIT) {
                        hasMoreData = false;
                    } else {
                        // Update start time to the last candle's close time + 1ms
                        JsonArray lastKline = klines.get(klines.size() - 1).getAsJsonArray();
                        long lastCloseTime = lastKline.get(6).getAsLong();
                        currentStartTime = lastCloseTime + 1;

                        // If we have an end time and we've passed it, stop
                        if (endTime != null && currentStartTime >= endTime) {
                            hasMoreData = false;
                        }
                    }

                    // Rate limiting delay
                    if (hasMoreData) {
                        Thread.sleep(RATE_LIMIT_DELAY_MS);
                    }
                }
            }   

            // Final progress update - 100%
            String progressBar = createProgressBar(100, 50);
            System.out.print(String.format("\r[%s] 100%% - Downloaded %d candles (%d requests)         \n",
                progressBar, allCandles.size(), requestCount));
            System.out.flush();

            log.info("Successfully downloaded {} candles from Binance for {}/{}",
                allCandles.size(), symbol, interval);

        } catch (InterruptedException e) {
            log.error("Download interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error downloading klines from Binance: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to download data from Binance: " + e.getMessage(), e);
        }

        return allCandles;
    }

    /**
     * Download kline data with string date parameters
     * @param symbol trading symbol
     * @param interval timeframe interval
     * @param startDate start date string (e.g., "2024-01-01")
     * @param endDate end date string (e.g., "2024-12-31")
     * @return list of candles
     */
    public List<Candle> downloadKlines(String symbol, String interval, String startDate, String endDate) {
        Long startTime = parseDateToMillis(startDate);
        Long endTime = parseDateToMillis(endDate);
        return downloadKlines(symbol, interval, startTime, endTime);
    }

    /**
     * Parse date string to milliseconds
     * Supports: "2024-01-01", "2024-01-01 00:00:00", or milliseconds as string
     */
    private Long parseDateToMillis(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            // Try parsing as long (milliseconds)
            return Long.parseLong(dateStr);
        } catch (NumberFormatException e) {
            // Try parsing as date
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(dateStr);
                return date.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli();
            } catch (Exception ex) {
                // Try parsing as datetime
                try {
                    java.time.LocalDateTime datetime = java.time.LocalDateTime.parse(
                        dateStr, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    return datetime.atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli();
                } catch (Exception ex2) {
                    log.error("Failed to parse date: {}", dateStr);
                    return null;
                }
            }
        }
    }

    /**
     * Test connection to Binance API
     * @return true if connection successful
     */
    public boolean testConnection() {
        try {
            Request request = new Request.Builder()
                .url(BASE_URL + "/fapi/v1/ping")
                .build();

            try (Response response = client.newCall(request).execute()) {
                boolean success = response.isSuccessful();
                if (success) {
                    log.info("Binance API connection test successful");
                } else {
                    log.error("Binance API connection test failed: {}", response.code());
                }
                return success;
            }
        } catch (Exception e) {
            log.error("Binance API connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get server time from Binance
     * @return server time in milliseconds
     */
    public long getServerTime() {
        try {
            Request request = new Request.Builder()
                .url(BASE_URL + "/fapi/v1/time")
                .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    long serverTime = JsonParser.parseString(responseBody).getAsJsonObject()
                        .get("serverTime").getAsLong();
                    return serverTime;
                }
            }
        } catch (Exception e) {
            log.error("Failed to get server time: {}", e.getMessage());
        }
        return System.currentTimeMillis();
    }

    /**
     * Convert interval string to milliseconds
     * @param interval interval string (e.g., "5m", "1h", "1d")
     * @return interval in milliseconds
     */
    private long getIntervalInMilliseconds(String interval) {
        String unit = interval.substring(interval.length() - 1);
        int value = Integer.parseInt(interval.substring(0, interval.length() - 1));

        switch (unit) {
            case "m": return value * 60L * 1000L;           // minutes
            case "h": return value * 60L * 60L * 1000L;     // hours
            case "d": return value * 24L * 60L * 60L * 1000L; // days
            case "w": return value * 7L * 24L * 60L * 60L * 1000L; // weeks
            case "M": return value * 30L * 24L * 60L * 60L * 1000L; // months (approximate)
            default: return 5L * 60L * 1000L; // default to 5 minutes
        }
    }

    /**
     * Create a progress bar string
     * @param percentage completion percentage (0-100)
     * @param width width of the progress bar
     * @return progress bar string
     */
    private String createProgressBar(int percentage, int width) {
        int filled = (int) (width * percentage / 100.0);
        int empty = width - filled;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            bar.append("█");
        }
        for (int i = 0; i < empty; i++) {
            bar.append("░");
        }
        return bar.toString();
    }
}
