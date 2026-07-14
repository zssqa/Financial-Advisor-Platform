package com.finance.advisor.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssetImportParser 单元测试（纯 Mockito 风格，不启动 Spring 上下文）。
 */
class AssetImportParserTest {

    private final AssetImportParser parser = new AssetImportParser();

    private MockMultipartFile csvFile(String filename, String content) {
        return new MockMultipartFile(
                "file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parseCsv_threeValidRows_successEqualsThreeAndNoFailures() throws IOException {
        String csv = """
                类型,代码,名称,数量,成本价,买入日期,备注
                股票,sh600036,招商银行,100,10.50,2026-01-15,长期持有
                基金,001001,华夏成长,200,1.20,2026-02-01,
                存款,,定期存款,1000,1.00,2026-03-01,银行存款
                """;
        AssetImportResult result = parser.parse(csvFile("assets.csv", csv));

        assertEquals(3, result.getSuccess());
        assertTrue(result.getFailed().isEmpty(), "失败行应为空");
        assertEquals(3, result.getAssets().size());
        // 验证首行资产字段解析正确
        Asset first = result.getAssets().get(0);
        assertEquals("stock", first.getType());
        assertEquals("sh600036", first.getSymbol());
        assertEquals("招商银行", first.getName());
        assertEquals(0, first.getAmount().compareTo(new java.math.BigDecimal("100")));
        assertEquals(0, first.getCostPrice().compareTo(new java.math.BigDecimal("10.50")));
        assertEquals(java.time.LocalDate.of(2026, 1, 15), first.getBuyDate());
    }

    @Test
    void parseCsv_oneInvalidAmountRow_successTwoAndOneFailureWithCorrectRow() throws IOException {
        String csv = """
                类型,代码,名称,数量,成本价,买入日期,备注
                股票,sh600036,招商银行,100,10.50,2026-01-15,长期持有
                股票,sh600000,浦发银行,abc,8.00,2026-02-01,
                存款,,定期存款,1000,1.00,2026-03-01,银行存款
                """;
        AssetImportResult result = parser.parse(csvFile("assets.csv", csv));

        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed().size());
        // 第二个数据行对应行号 3（表头为第 1 行）
        AssetImportResult.FailedRow failedRow = result.getFailed().get(0);
        assertEquals(3, failedRow.getRow());
        assertNotNull(failedRow.getReason());
    }

    @Test
    void parseCsv_withUtf8Bom_parsesTypesCorrectly() throws IOException {
        String csv = "类型,代码,名称,数量,成本价,买入日期,备注\n"
                + "股票,sh600036,招商银行,100,10.50,2026-01-15,长期持有\n"
                + "基金,110011,易方达中小盘,500,3.20,2026-02-01,定投";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        bos.write(csv.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = bos.toByteArray();

        MockMultipartFile file = new MockMultipartFile("file", "bom.csv", "text/csv", bytes);
        AssetImportResult result = parser.parse(file);

        assertEquals(2, result.getSuccess());
        assertEquals(2, result.getAssets().size());
        assertEquals("stock", result.getAssets().get(0).getType());
        assertEquals("fund", result.getAssets().get(1).getType());
        assertTrue(result.getFailed().isEmpty(), "失败行应为空");
    }

    @Test
    void parseUnsupportedExtension_throwsIllegalArgumentException() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> parser.parse(txt));
    }
}
