package com.vegas.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 記錄交易入場時的完整市場狀態
 * 用於分析虧損交易的共通點
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeContext {

    // ==================== 時間相關 ====================

    /**
     * 入場時的小時 (0-23 UTC)
     */
    private int entryHour;

    /**
     * 入場時的星期幾 (1=Monday, 7=Sunday)
     */
    private int entryDayOfWeek;

    /**
     * 入場時的月份 (1-12)
     */
    private int entryMonth;

    // ==================== 趨勢指標 ====================

    /**
     * ADX 值 (趨勢強度)
     */
    private double adx;

    /**
     * EMA12 值
     */
    private double ema12;

    /**
     * EMA144 值
     */
    private double ema144;

    /**
     * EMA169 值
     */
    private double ema169;

    /**
     * EMA576 值
     */
    private double ema576;

    /**
     * EMA676 值
     */
    private double ema676;

    // ==================== 價格相關 ====================

    /**
     * 入場價格
     */
    private double entryPrice;

    /**
     * 入場時的 K 線成交量
     */
    private double entryVolume;

    /**
     * 最近 N 根 K 線的平均成交量
     */
    private double avgVolume;

    /**
     * 成交量比率 (當前/平均)
     */
    private double volumeRatio;

    /**
     * 止損距離百分比
     */
    private double stopLossDistancePercent;

    /**
     * 止盈距離百分比
     */
    private double takeProfitDistancePercent;

    // ==================== 波動性相關 ====================

    /**
     * ATR (Average True Range) - 14 期
     */
    private double atr;

    /**
     * ATR 百分比 (ATR / 價格)
     */
    private double atrPercent;

    /**
     * 最近 N 根 K 線的價格範圍 (高-低)
     */
    private double recentRange;

    // ==================== EMA 結構相關 ====================

    /**
     * 價格與 EMA12 的距離百分比
     */
    private double distanceFromEma12Percent;

    /**
     * 價格與 EMA144 的距離百分比
     */
    private double distanceFromEma144Percent;

    /**
     * EMA144 與 EMA169 的距離百分比
     */
    private double ema144To169DistancePercent;

    /**
     * 是否為金叉狀態 (EMA144 > EMA169)
     */
    private boolean goldenCross;

    /**
     * 是否為死叉狀態 (EMA144 < EMA169)
     */
    private boolean deathCross;

    // ==================== K 線形態相關 ====================

    /**
     * 入場 K 線是否為陽線
     */
    private boolean bullishCandle;

    /**
     * K 線實體大小百分比 (|close-open| / (high-low))
     */
    private double candleBodyPercent;

    /**
     * 上影線百分比
     */
    private double upperWickPercent;

    /**
     * 下影線百分比
     */
    private double lowerWickPercent;

    // ==================== 連續性相關 ====================

    /**
     * 最近連續盈利交易數量
     */
    private int consecutiveWins;

    /**
     * 最近連續虧損交易數量
     */
    private int consecutiveLosses;

    /**
     * 與上一筆交易的間隔（小時）
     */
    private double hoursSinceLastTrade;

    // ==================== 輔助方法 ====================

    /**
     * 從時間戳提取時間信息
     */
    public static TradeContextBuilder fromTimestamp(long timestamp) {
        ZonedDateTime zdt = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC"));
        return TradeContext.builder()
            .entryHour(zdt.getHour())
            .entryDayOfWeek(zdt.getDayOfWeek().getValue())
            .entryMonth(zdt.getMonthValue());
    }

    /**
     * 獲取星期幾的名稱
     */
    public String getDayOfWeekName() {
        return DayOfWeek.of(entryDayOfWeek).toString();
    }

    /**
     * 檢查是否為歐美交易時段 (08:00 - 20:00 UTC)
     */
    public boolean isEuropeanUSSession() {
        return entryHour >= 8 && entryHour <= 20;
    }

    /**
     * 檢查是否為亞洲交易時段 (00:00 - 08:00 UTC)
     */
    public boolean isAsianSession() {
        return entryHour >= 0 && entryHour < 8;
    }

    /**
     * 檢查是否為週末前（星期五）
     */
    public boolean isFriday() {
        return entryDayOfWeek == 5;
    }

    /**
     * 檢查是否為週一
     */
    public boolean isMonday() {
        return entryDayOfWeek == 1;
    }

    /**
     * 檢查成交量是否高於平均
     */
    public boolean isHighVolume() {
        return volumeRatio > 1.5;
    }

    /**
     * 檢查成交量是否低於平均
     */
    public boolean isLowVolume() {
        return volumeRatio < 0.5;
    }

    /**
     * 檢查 ADX 是否處於強趨勢
     */
    public boolean isStrongTrend() {
        return adx > 50;
    }

    /**
     * 檢查 ADX 是否處於極強趨勢
     */
    public boolean isVeryStrongTrend() {
        return adx > 60;
    }

    /**
     * 檢查是否過度延伸 (價格離 EMA12 太遠)
     */
    public boolean isOverextended() {
        return Math.abs(distanceFromEma12Percent) > 2.0;
    }
}
