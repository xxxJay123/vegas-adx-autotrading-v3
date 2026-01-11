# Vegas + ADX Backtesting System v2.0

**Production-grade backtesting system** for the 2025 Ultimate Vegas + ADX strategy with 16 independent trading rules.

Built by a world-class senior crypto quantitative engineer - this is your main backtesting weapon for 2025-2026.

## 🚀 Features

- **16 Independent Rules** - 8 sophisticated long rules + 8 mirrored short rules, each can be enabled/disabled
- **✨ NEW: Multiple Timeframes** - Backtest across 5m, 15m, 1h, 4h, 1d, or any Binance timeframe
- **✨ NEW: Binance API Integration** - Download historical data directly from Binance
- **Auto Data Discovery** - Automatically scans `./data/kline/` folder and runs year-by-year backtests
- **Fixed Notional Sizing** - Real CEX-style position sizing: qty = (100 USDT × leverage) / entry_price
- **Production-Ready** - Pure Java 17 + Maven, zero hardcoded values, zero TODOs
- **TradingView Dark Theme** - Beautiful interactive HTML reports with Plotly charts
- **Fully Configurable** - Everything loaded from `.env` file
- **Incremental Indicators** - Zero-allocation EMA and ADX after warm-up
- **Flexible Data Sources** - Switch between CSV files and Binance API via configuration

## Features

- ✅ **Perpetual Futures Engine**: Full support for leverage (1x-125x), maker/taker fees, precise position sizing
- ✅ **16 Advanced Entry Rules**: 8 long rules + 8 short rules with EMA patterns, 2B formations, trend analysis
- ✅ **Technical Indicators**: EMA (12, 144, 169, 576, 676) and ADX with configurable periods
- ✅ **Risk Management**: Percentage-based position sizing, dynamic stop-loss, and take-profit
- ✅ **Beautiful Reports**: TradingView-style HTML reports with interactive Plotly charts
- ✅ **Fully Configurable**: All parameters loaded from `.env` file, individual rule enable/disable
- ✅ **Production Ready**: Clean Java 17 code, comprehensive logging, error handling

## ⚡ Quick Start

### Prerequisites
- **Java 17+** ([Download](https://adoptium.net/))
- **Maven 3.8+** ([Download](https://maven.apache.org/download.cgi))

### Option 1: CSV Files (Traditional Method)

1. **Prepare your data** - Place CSV files in `./data/kline/` folder:
   ```
   ./data/kline/kline_BTCUSDT_2024_5m.csv
   ./data/kline/kline_BTCUSDT_2024_15m.csv
   ./data/kline/kline_BTCUSDT_2024_1h.csv
   ```

2. **Configure** - Edit `.env` file:
   ```properties
   DATA_SOURCE=csv
   TIMEFRAMES=5m,15m,1h
   ```

3. **Run:**
   ```bash
   mvn clean package && java -jar target/backtest-1.0.0.jar
   ```

### Option 2: Binance API (New - Automatic Download)

1. **Configure** - Edit `.env` file:
   ```properties
   DATA_SOURCE=binance
   TIMEFRAMES=5m,15m,1h
   BINANCE_SYMBOL=BTCUSDT
   BINANCE_START_DATE=2024-01-01
   BINANCE_END_DATE=2024-12-31
   # API keys optional for public data
   BINANCE_API_KEY=
   BINANCE_SECRET_KEY=
   ```

2. **Run:**
   ```bash
   mvn clean package && java -jar target/backtest-1.0.0.jar
   ```

The system will automatically:
- Download data from Binance for each timeframe
- Save CSV files to `./data/kline/`
- Run backtests for each timeframe
- Generate HTML reports in `./data/reports/`
- Open the last report in your browser

📖 **Detailed Binance Setup**: See [BINANCE_SETUP.md](BINANCE_SETUP.md) for complete guide

## 📋 Strategy: 16 Independent Rules (All require ADX > threshold)

### Long Rules
1. **Rule 1**: 1st cross above EMA12 after touching min(EMA576, EMA676)
2. **Rule 2**: 2nd cross above EMA12 after touching min(EMA576, EMA676) again
3. **Rule 3**: 1st cross + 2B pattern after touching min(EMA144, EMA169)
4. **Rule 4**: 2nd cross after touching min(EMA144, EMA169) (no 2B needed)
5. **Rule 5**: Trend continuation - was above EMA12 → pullback → 2nd cross up
6. **Rule 6**: Bear trend double bottom → 3rd fake break + 2B + cross EMA12
7. **Rule 7**: EMA144 already golden cross EMA169 → pullback → cross EMA12
8. **Rule 8**: Bear trend, EMA12 in middle → touch mid zone → 2nd cross up

### Short Rules
**Perfect mirror** of the 8 long rules with opposite logic (cross below, max instead of min, etc.)

## ⚙️ Configuration (.env)

All settings are in `.env` file - NO hardcoded values!

### Position Sizing (Real CEX Style)
```properties
FIXED_NOTIONAL_USDT=100    # Every trade uses 100 USDT notional
LEVERAGE=50                # 1-125x leverage
```
Contract quantity = (100 × 50) / entry_price - Works perfectly for BTC, ETH, SOL, 1000PEPE!

### Risk & Reward
```properties
STOP_LOOKBACK=136          # SL = lowest low of last N bars (long)
REWARD_RATIO=3.7           # TP = risk × 3.7
MIN_REWARD_RATIO=2.0       # Minimum reward ratio enforcement
```

### Fees
```properties
MAKER_FEE_PERCENT=0.02     # 0.02% (limit orders)
TAKER_FEE_PERCENT=0.075    # 0.075% (market orders)
```

### Indicators
```properties
EMA12_LEN=12
EMA144_LEN=144
EMA169_LEN=169
EMA576_LEN=576
EMA676_LEN=676
ADX_PERIOD=14
ADX_THRESHOLD=30           # Only enter when ADX > 30
```

### Enable/Disable Rules (Independent)
```properties
LONG_RULE_1_ENABLE=true
LONG_RULE_2_ENABLE=true
# ... all 8 long rules
SHORT_RULE_1_ENABLE=true
SHORT_RULE_2_ENABLE=true
# ... all 8 short rules
```

## 📊 Data Format

### File Naming Pattern

#### New Format (with timeframe support)
```
kline_SYMBOL_YEAR_TIMEFRAME.csv
```

Examples:
- `kline_BTCUSDT_2024_5m.csv`
- `kline_ETHUSDT_2024_15m.csv`
- `kline_SOLUSDT_2024_1h.csv`

#### Legacy Format (backward compatible, defaults to 5m)
```
kline_SYMBOL.P_YEAR.csv
```

Examples:
- `kline_BTCUSDT.P_2024.csv` → Treated as 5m
- `kline_ETHUSDT.P_2023.csv` → Treated as 5m

### CSV Format
```csv
timestamp,open,high,low,close,volume
1609459200000,28923.63,29600.00,28802.00,29374.15,45678.23
1609462800000,29374.15,29500.00,29100.00,29234.56,34567.89
...
```

- **timestamp**: Unix milliseconds OR ISO format (e.g., `2021-01-01T00:00:00Z`)
- **open, high, low, close**: Price data
- **volume**: Trading volume

Place all CSV files in `./data/kline/` directory.

### Supported Timeframes
- Minutes: `1m`, `3m`, `5m`, `15m`, `30m`
- Hours: `1h`, `2h`, `4h`, `6h`, `8h`, `12h`
- Days: `1d`, `3d`
- Weeks: `1w`
- Months: `1M`

## Understanding the HTML Report

The generated report includes:

### Summary Metrics
- Net Profit ($ and %)
- Max Drawdown
- Total Trades & Win Rate
- Profit Factor, Sharpe Ratio
- Average/Largest Win/Loss

### Equity Curve Chart
- **Teal line**: Equity over time
- **Red/Green bars**: Drawdown underwater chart
- Interactive Plotly chart with zoom/pan

### Trade List
All trades with entry/exit details, P&L, and which rule triggered entry

### Monthly Returns & Rule Performance
Performance breakdown by month and by entry rule

## Advanced Usage

### Test Specific Rules Only
```properties
# Test only Rule 1 and 2 for longs
ENABLE_LONG_RULE1=true
ENABLE_LONG_RULE2=true
ENABLE_LONG_RULE3=false
# ... disable others

# Disable all shorts
ENABLE_SHORT_RULE1=false
# ... etc
```

### Multiple Configurations
Create multiple `.env` files:
```bash
.env.conservative  # Lower leverage, wider stops
.env.aggressive    # Higher leverage, tighter stops
.env.longonly      # Only long trades
```

Run with different configs:
```bash
cp .env.conservative .env && mvn clean package && java -jar target/vegas-adx-backtest-1.0.0.jar
```

### Getting Real Data (Python)
```python
import pandas as pd
from binance.client import Client

client = Client()
klines = client.get_historical_klines("BTCUSDT", "1m", "1 Jan 2024", "1 Feb 2024")
df = pd.DataFrame(klines)[:, :6]
df.columns = ['timestamp', 'open', 'high', 'low', 'close', 'volume']
df.to_csv('data/BTCUSDT_1m.csv', index=False)
```

## 📁 Project Structure

```
vegas-adx-autotrading-v2/
├── pom.xml                                          # Maven dependencies
├── .env / .env.example                              # Configuration
├── src/main/java/com/vegas/backtest/
│   ├── Main.java                                    # Auto-discovery & orchestration
│   ├── config/
│   │   └── Config.java                              # .env loader (singleton)
│   ├── model/
│   │   ├── Candle.java                              # OHLCV data
│   │   ├── Trade.java                               # Completed trade
│   │   ├── Position.java                            # Active position
│   │   └── BacktestResult.java                      # Results + statistics
│   ├── indicator/
│   │   ├── EMA.java                                 # Incremental EMA
│   │   └── ADX.java                                 # Incremental ADX
│   ├── strategy/
│   │   └── VegasADXStrategy.java                    # 16 rules implementation
│   ├── engine/
│   │   └── BacktestEngine.java                      # Backtesting engine
│   ├── reporter/
│   │   └── HtmlReportGenerator.java                 # TradingView dark theme
│   └── util/
│       ├── DataFileScanner.java                     # Auto file discovery
│       └── CsvDataLoader.java                       # CSV parser
├── data/
│   ├── kline/                                       # CSV data files (auto-scanned)
│   └── reports/                                     # Generated HTML reports
└── logs/                                            # Application logs
```

## 🔧 How It Works

**Position Sizing (CEX Style)**:
```
Contract Quantity = (FIXED_NOTIONAL_USDT × LEVERAGE) / entry_price
Example: (100 USDT × 50x) / 30000 = 0.1667 BTC
```

**Stop Loss**:
- Long: Lowest low of last STOP_LOOKBACK bars
- Short: Highest high of last STOP_LOOKBACK bars
- NO minimum distance check (allows super tight stops)

**Take Profit**:
```
risk = |entry_price - stop_loss|
effective_ratio = max(REWARD_RATIO, MIN_REWARD_RATIO)
TP = entry_price ± (risk × effective_ratio)
```

**Fees**:
- Entry: TAKER_FEE on notional × leverage
- Exit: MAKER_FEE (if TP) or TAKER_FEE (if SL)
- Deducted from P&L

## 🛠️ Troubleshooting

| Problem | Solution |
|---------|----------|
| No files discovered | Check `./data/kline/` exists and files match pattern `kline_SYMBOL.P_YEAR.csv` |
| CSV parse errors | Verify format: `timestamp,open,high,low,close,volume` with header |
| No trades generated | Lower `ADX_THRESHOLD`, enable more rules, ensure 700+ candles for warm-up |
| Reports not opening | Check `./data/reports/` folder, open `.html` files manually |
| OutOfMemoryError | Increase heap: `java -Xmx4g -jar target/backtest-1.0.0.jar` |

## Logs

Application logs: `logs/backtest.log` (daily rotation)

Adjust level in `src/main/resources/logback.xml`:
```xml
<logger name="com.trading" level="DEBUG"/>  <!-- Change to DEBUG for verbose -->
```

## 🆕 New Features: Multi-Timeframe & Binance Integration

### Multiple Timeframes
The system now supports backtesting across multiple timeframes simultaneously:

```properties
# In .env file
TIMEFRAMES=5m,15m,1h,4h
```

**Benefits**:
- Compare strategy performance across different timeframes
- Discover which timeframe is most profitable for your strategy
- Run parallel backtests with a single command
- Each timeframe gets its own detailed report

### Binance API Integration
Download historical Kline data directly from Binance:

```properties
# In .env file
DATA_SOURCE=binance
BINANCE_SYMBOL=BTCUSDT
BINANCE_START_DATE=2024-01-01
BINANCE_END_DATE=2024-12-31
TIMEFRAMES=5m,15m,1h
```

**Features**:
- ✅ Automatic data download - no manual CSV preparation
- ✅ Support for all Binance spot symbols
- ✅ Rate limiting to avoid API restrictions
- ✅ Downloaded data cached as CSV for fast re-runs
- ✅ Optional API keys (public data doesn't require authentication)
- ✅ Comprehensive error handling

**See [BINANCE_SETUP.md](BINANCE_SETUP.md) for complete setup guide including**:
- How to get Binance API keys
- Configuration options
- Usage examples
- Troubleshooting
- Performance tips

### Migration from Old Version

Your existing CSV files still work! The system supports both formats:

**Old format** (still works):
```
kline_BTCUSDT.P_2024.csv  → Automatically treated as 5m
```

**New format** (recommended):
```
kline_BTCUSDT_2024_5m.csv  → Explicit timeframe
```

No code changes needed - just update your `.env` file to use new features!

## License & Disclaimer

**For educational and research purposes only.** Use at your own risk. Past performance does not guarantee future results.

---

**Happy Backtesting Across Multiple Timeframes!** 🚀📈

