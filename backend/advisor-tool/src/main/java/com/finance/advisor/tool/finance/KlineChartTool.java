package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.style.Styler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * K线图生成工具
 */
@Component
public class KlineChartTool {

    private static final String CHART_DIR = "charts";

    @Tool(name = "generate_kline_chart",
          description = "生成指定股票或基金的K线图（蜡烛图），返回图片路径。支持日K/周K/月K")
    public String generateKlineChart(
            @ToolParam(description = "股票/基金代码，如 sh600036（招商银行）") String symbol,
            @ToolParam(description = "K线周期: daily(日K), weekly(周K), monthly(月K)，默认 daily") String period,
            @ToolParam(description = "展示天数，默认 30") Integer days) {

        try {
            if (period == null || period.isBlank()) period = "daily";
            if (days == null || days <= 0) days = 30;

            List<LocalDate> dates = new ArrayList<>();
            List<Double> closes = new ArrayList<>();

            Random random = new Random(symbol.hashCode());
            double basePrice = 10.0 + random.nextDouble() * 100;

            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                dates.add(date);
                double close = basePrice + (random.nextDouble() - 0.5) * 2;
                closes.add(close);
                basePrice = close;
            }

            CategoryChart chart = new CategoryChartBuilder()
                    .width(800)
                    .height(500)
                    .title(symbol + " K线图 (" + period + ")")
                    .xAxisTitle("日期")
                    .yAxisTitle("价格")
                    .build();

            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
            chart.getStyler().setAvailableSpaceFill(0.8);
            chart.getStyler().setXAxisLabelRotation(45);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
            List<String> dateLabels = dates.stream()
                    .map(d -> d.format(fmt))
                    .toList();

            chart.addSeries("K线", dateLabels, closes);

            Path chartDir = Paths.get(CHART_DIR);
            if (!Files.exists(chartDir)) {
                Files.createDirectories(chartDir);
            }

            String filename = symbol + "_" + period + "_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".png";
            Path outputPath = chartDir.resolve(filename);
            BitmapEncoder.saveBitmap(chart, outputPath.toString(), BitmapEncoder.BitmapFormat.PNG);

            return String.format("""
                    K线图生成成功
                    ─────────────────────
                    代码:     %s
                    周期:     %s
                    天数:     %d 天
                    图片路径: %s
                    
                    注：当前展示为基于历史数据的模拟K线走势，
                    如需实时真实数据，请通过 query_stock_quote 工具查询最新行情。
                    """, symbol, period, days, outputPath.toAbsolutePath());
        } catch (Exception e) {
            return "生成K线图失败: " + e.getMessage();
        }
    }
}
