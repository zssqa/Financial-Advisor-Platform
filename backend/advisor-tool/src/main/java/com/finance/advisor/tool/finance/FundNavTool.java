package com.finance.advisor.tool.finance;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基金净值查询工具
 */
@Component
public class FundNavTool {

    private final RestTemplate restTemplate;

    public FundNavTool() {
        this.restTemplate = new RestTemplate();
        this.restTemplate.getInterceptors().add((ClientHttpRequestInterceptor) (request, body, execution) -> {
            request.getHeaders().add("Referer", "https://fundf10.eastmoney.com/");
            return execution.execute(request, body);
        });
    }

    private static final Pattern DWJZ_PATTERN = Pattern.compile("\"DWJZ\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FSRQ_PATTERN = Pattern.compile("\"FSRQ\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern JZZZL_PATTERN = Pattern.compile("\"JZZZL\"\\s*:\\s*\"([^\"]+)\"");

    @Tool(name = "query_fund_nav",
          description = "查询基金的最新单位净值、累计净值、日涨跌幅和阶段收益")
    public String queryFundNav(
            @ToolParam(description = "基金代码，如 110011（易方达中小盘）") String fundCode) {
        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz?"
                    + "callback=jQuery&fundCode=" + fundCode + "&pageIndex=1&pageSize=30";
            // 用 byte[] 接收，显式 UTF-8 解码，避免编码问题
            byte[] bytes = restTemplate.getForObject(url, byte[].class);
            if (bytes == null || bytes.length == 0) {
                return "未查询到基金 " + fundCode + " 的数据";
            }
            String response = new String(bytes, StandardCharsets.UTF_8);

            int start = response.indexOf('(');
            int end = response.lastIndexOf(')');
            if (start == -1 || end == -1 || end <= start) {
                return "数据格式异常";
            }
            String jsonData = response.substring(start + 1, end);

            // 提取 Data.LSJZList[0] 第一条记录，确保 DWJZ/FSRQ/JZZZL 来自同一对象
            int listIdx = jsonData.indexOf("\"LSJZList\"");
            if (listIdx == -1) {
                return "未查询到基金 " + fundCode + " 的净值数据";
            }
            int firstBrace = jsonData.indexOf('{', listIdx);
            int firstClose = firstBrace != -1 ? jsonData.indexOf('}', firstBrace) : -1;
            if (firstBrace == -1 || firstClose == -1) {
                return "未查询到基金 " + fundCode + " 的净值数据";
            }
            String firstEntry = jsonData.substring(firstBrace, firstClose + 1);

            Matcher dwjzMatcher = DWJZ_PATTERN.matcher(firstEntry);
            if (!dwjzMatcher.find()) {
                return "未查询到基金 " + fundCode + " 的净值数据";
            }
            String dwjzStr = dwjzMatcher.group(1);

            Matcher fsrqMatcher = FSRQ_PATTERN.matcher(firstEntry);
            String fsrq = fsrqMatcher.find() ? fsrqMatcher.group(1) : "";

            Matcher jzzzlMatcher = JZZZL_PATTERN.matcher(firstEntry);
            String jzzzlStr = jzzzlMatcher.find() ? jzzzlMatcher.group(1) : "";

            BigDecimal nav = new BigDecimal(dwjzStr).setScale(4, RoundingMode.HALF_UP);
            StringBuilder sb = new StringBuilder();
            sb.append("基金代码: ").append(fundCode).append("\n");
            sb.append("最新净值: ").append(nav.toPlainString()).append("\n");
            if (!fsrq.isBlank()) {
                sb.append("净值日期: ").append(fsrq).append("\n");
            }
            if (!jzzzlStr.isBlank()) {
                BigDecimal jzzzl = new BigDecimal(jzzzlStr).setScale(2, RoundingMode.HALF_UP);
                String sign = jzzzl.signum() >= 0 ? "+" : "";
                sb.append("日涨跌幅: ").append(sign).append(jzzzl.toPlainString()).append("%");
            }
            return sb.toString();
        } catch (Exception e) {
            return "查询基金净值失败: " + e.getMessage();
        }
    }
}
