package com.finance.advisor.portfolio;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 资产导入文件解析器：将上传的 xlsx/xls/csv 文件解析为 Asset 列表，并记录失败行。
 *
 * 表头支持中英文（大小写不敏感）：
 *   类型/type、代码/symbol、名称/name、数量/amount、成本价/costPrice、买入日期/buyDate、备注/notes
 */
@Component
public class AssetImportParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 中文表头 → 英文字段名 */
    private static final Map<String, String> HEADER_CN_TO_EN = Map.of(
            "类型", "type",
            "代码", "symbol",
            "名称", "name",
            "数量", "amount",
            "成本价", "costPrice",
            "买入日期", "buyDate",
            "备注", "notes"
    );

    /** 中文类型 → 英文类型 */
    private static final Map<String, String> TYPE_CN_TO_EN = Map.of(
            "股票", "stock",
            "基金", "fund",
            "存款", "deposit",
            "债券", "bond",
            "现金", "cash",
            "其他", "other"
    );

    private static final Set<String> VALID_TYPES = Set.of("stock", "fund", "deposit", "bond", "cash", "other");

    public AssetImportResult parse(MultipartFile file) throws IOException {
        AssetImportResult result = new AssetImportResult();
        String filename = file.getOriginalFilename();
        String ext = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase()
                : "";
        switch (ext) {
            case "xlsx", "xls" -> parseExcel(file, result);
            case "csv" -> parseCsv(file, result);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + (ext.isEmpty() ? "未知" : ext));
        }
        result.setSuccess(result.getAssets().size());
        return result;
    }

    private void parseExcel(MultipartFile file, AssetImportResult result) throws IOException {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return;
            }
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerIndex = buildHeaderIndex(readExcelHeader(headerRow));
            if (headerIndex.isEmpty()) {
                return;
            }
            int lastRow = sheet.getLastRowNum();
            // 数据行从 sheet 第 1 行开始（表头在第 0 行），对应错误上报行号为 sheetRowIndex + 1
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = readExcelRow(row, headerIndex);
                int rowNum = r + 1;
                if (isBlankRow(values)) {
                    continue;
                }
                processRow(values, rowNum, result);
            }
        }
    }

    private void parseCsv(MultipartFile file, AssetImportResult result) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            Map<String, Integer> headerIndex = buildHeaderIndex(splitLine(headerLine));
            if (headerIndex.isEmpty()) {
                return;
            }
            String line;
            int lineNo = 1; // 表头所在行号
            while ((line = reader.readLine()) != null) {
                lineNo++; // 数据行行号，从 2 开始
                Map<String, String> values = readCsvRow(splitLine(line), headerIndex);
                if (isBlankRow(values)) {
                    continue;
                }
                processRow(values, lineNo, result);
            }
        }
    }

    private String[] readExcelHeader(Row headerRow) {
        if (headerRow == null) {
            return new String[0];
        }
        int last = headerRow.getLastCellNum();
        String[] headers = new String[last < 0 ? 0 : last];
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.getCell(i);
            headers[i] = cell == null ? "" : getCellValueAsString(cell);
        }
        return headers;
    }

    private Map<String, Integer> buildHeaderIndex(String[] headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String field = normalizeHeader(headers[i]);
            if (field != null && !index.containsKey(field)) {
                index.put(field, i);
            }
        }
        return index;
    }

    private Map<String, String> readExcelRow(Row row, Map<String, Integer> headerIndex) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, Integer> e : headerIndex.entrySet()) {
            Cell cell = row.getCell(e.getValue());
            values.put(e.getKey(), cell == null ? "" : getCellValueAsString(cell).trim());
        }
        return values;
    }

    private Map<String, String> readCsvRow(String[] parts, Map<String, Integer> headerIndex) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, Integer> e : headerIndex.entrySet()) {
            int idx = e.getValue();
            values.put(e.getKey(), idx >= 0 && idx < parts.length ? parts[idx].trim() : "");
        }
        return values;
    }

    private boolean isBlankRow(Map<String, String> values) {
        if (values.isEmpty()) {
            return true;
        }
        for (String v : values.values()) {
            if (v != null && !v.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void processRow(Map<String, String> values, int rowNum, AssetImportResult result) {
        // type
        String typeRaw = values.getOrDefault("type", "");
        String type = normalizeType(typeRaw);
        if (type == null) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "类型无效: " + typeRaw));
            return;
        }
        // name
        String name = values.getOrDefault("name", "");
        if (name == null || name.isEmpty()) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "名称不能为空"));
            return;
        }
        // amount
        BigDecimal amount;
        String amountRaw = values.getOrDefault("amount", "");
        try {
            amount = new BigDecimal(amountRaw);
        } catch (Exception e) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "数量格式无效: " + amountRaw));
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "数量必须大于0"));
            return;
        }
        // costPrice
        BigDecimal costPrice = null;
        String cpRaw = values.getOrDefault("costPrice", "");
        if (cpRaw == null || cpRaw.isEmpty()) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "成本价不能为空"));
            return;
        }
        try {
            costPrice = new BigDecimal(cpRaw);
        } catch (Exception e) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "成本价格式无效: " + cpRaw));
            return;
        }
        if (costPrice.compareTo(BigDecimal.ZERO) < 0) {
            result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "成本价不能为负"));
            return;
        }
        // symbol (optional)
        String symbol = values.getOrDefault("symbol", "");
        if (symbol == null || symbol.isEmpty()) {
            symbol = null;
        }
        // buyDate (optional, yyyy-MM-dd)
        LocalDate buyDate = null;
        String bdRaw = values.getOrDefault("buyDate", "");
        if (bdRaw != null && !bdRaw.isEmpty()) {
            try {
                buyDate = LocalDate.parse(bdRaw, DATE_FMT);
            } catch (DateTimeParseException e) {
                result.getFailed().add(new AssetImportResult.FailedRow(rowNum, "买入日期格式无效，应为 yyyy-MM-dd: " + bdRaw));
                return;
            }
        }
        // notes (optional)
        String notes = values.getOrDefault("notes", "");
        if (notes == null || notes.isEmpty()) {
            notes = null;
        }

        Asset asset = new Asset();
        asset.setType(type);
        asset.setSymbol(symbol);
        asset.setName(name);
        asset.setAmount(amount);
        asset.setCostPrice(costPrice);
        asset.setBuyDate(buyDate);
        asset.setNotes(notes);
        result.getAssets().add(asset);
    }

    private String normalizeHeader(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String cn = HEADER_CN_TO_EN.get(trimmed);
        if (cn != null) {
            return cn;
        }
        String lower = trimmed.toLowerCase();
        return switch (lower) {
            case "type" -> "type";
            case "symbol" -> "symbol";
            case "name" -> "name";
            case "amount" -> "amount";
            case "costprice" -> "costPrice";
            case "buydate" -> "buyDate";
            case "notes" -> "notes";
            default -> null;
        };
    }

    private String normalizeType(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        String cn = TYPE_CN_TO_EN.get(trimmed);
        if (cn != null) {
            return cn;
        }
        String lower = trimmed.toLowerCase();
        return VALID_TYPES.contains(lower) ? lower : null;
    }

    private String[] splitLine(String line) {
        if (line == null) {
            return new String[0];
        }
        return line.split(",", -1);
    }

    private String getCellValueAsString(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    java.util.Date date = cell.getDateCellValue();
                    return date != null ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(date) : "";
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    return String.valueOf((long) num);
                }
                return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
