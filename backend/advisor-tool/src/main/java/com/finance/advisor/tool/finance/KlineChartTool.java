package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.knowm.xchart.OHLCChart;
import org.knowm.xchart.OHLCChartBuilder;
import org.knowm.xchart.OHLCSeries;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.style.Styler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * K线图生成工具
 * 基于真实行情数据生成蜡烛图（OHLC图）
 */
@Component
public class KlineChartTool {

    private static final String CHART_DIR = "charts";

    private final KlineFetcher klineFetcher;

    public KlineChartTool() {
        this.klineFetcher = new KlineFetcher();
    }

    @Tool(name = "generate_kline_chart",
          description = "生成指定股票或基金的K线图（蜡烛图），返回图片路径。支持日K/周K/月K")
    public String generateKlineChart(
            @ToolParam(description = "股票/基金代码，如 sh600036（招商银行）") String symbol,
            @ToolParam(description = "K线周期: daily(日K), weekly(周K), monthly(月K)，默认 daily") String period,
            @ToolParam(description = "展示天数，默认 30") Integer days) {

        try {
            if (period == null || period.isBlank()) {
                period = "daily";
            }
            if (days == null || days <= 0) {
                days = 30;
            }

            // 调用 KlineFetcher 获取真实K线数据（最多60个交易日）
            List<KlineFetcher.KlineData> allData = klineFetcher.fetchKlineData(symbol, 60);
            if (allData == null || allData.isEmpty()) {
                return String.format("""
                        获取K线数据失败
                        ─────────────────────
                        代码: %s
                        原因: 行情API无可用数据，请检查股票代码是否正确或稍后重试。
                        """, symbol);
            }

            // 截取最近 days 条数据用于展示
            int fromIndex = Math.max(0, allData.size() - days);
            List<KlineFetcher.KlineData> data = allData.subList(fromIndex, allData.size());

            // 构建OHLC蜡烛图
            OHLCChart chart = new OHLCChartBuilder()
                    .width(1200)
                    .height(600)
                    .title(symbol + " K线图 (" + period + ")")
                    .xAxisTitle("日期")
                    .yAxisTitle("价格")
                    .build();

            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
            chart.getStyler().setXAxisLabelRotation(45);
            chart.getStyler().setDefaultSeriesRenderStyle(OHLCSeries.OHLCSeriesRenderStyle.Candle);
            chart.getStyler().setDatePattern("MM/dd");

            // 准备OHLC数据列
            List<Date> xData = new ArrayList<>();
            List<Double> openData = new ArrayList<>();
            List<Double> highData = new ArrayList<>();
            List<Double> lowData = new ArrayList<>();
            List<Double> closeData = new ArrayList<>();

            SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd");
            for (KlineFetcher.KlineData k : data) {
                // 日期格式可能为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss，取前10位
                String dayStr = k.getDay().length() >= 10 ? k.getDay().substring(0, 10) : k.getDay();
                Date date = dateParser.parse(dayStr);
                xData.add(date);
                openData.add(k.getOpen());
                highData.add(k.getHigh());
                lowData.add(k.getLow());
                closeData.add(k.getClose());
            }

            chart.addSeries("K线", xData, openData, highData, lowData, closeData);

            // 输出PNG到 charts/ 目录
            Path chartDir = Paths.get(CHART_DIR);
            if (!Files.exists(chartDir)) {
                Files.createDirectories(chartDir);
            }

            String filename = symbol + "_" + period + "_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".png";
            Path outputPath = chartDir.resolve(filename);
            BitmapEncoder.saveBitmap(chart, outputPath.toString(), BitmapEncoder.BitmapFormat.PNG);

            // 统计区间涨跌信息
            double firstClose = data.get(0).getClose();
            double lastClose = data.get(data.size() - 1).getClose();
            double changePercent = ((lastClose - firstClose) / firstClose) * 100;
            String trend = changePercent >= 0 ? "+" : "";

            return String.format("""
                    K线图生成成功
                    ─────────────────────
                    代码:     %s
                    周期:     %s
                    天数:     %d 天
                    图片路径: %s
                    区间涨跌: %s%.2f%%
                    起始收盘: %.2f 元
                    最新收盘: %.2f 元

                    注：数据来源于新浪/东方财富实时行情API。
                    """, symbol, period, data.size(), outputPath.toAbsolutePath(),
                    trend, changePercent, firstClose, lastClose);
        } catch (Exception e) {
            return "生成K线图失败: " + e.getMessage();
        }
    }
}
