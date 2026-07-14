package com.finance.advisor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 文档读取器
 */
public class ExcelDocumentReader implements DocumentReader {

    private final Resource resource;

    public ExcelDocumentReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        List<Document> documents = new ArrayList<>();
        try (InputStream is = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                StringBuilder sheetContent = new StringBuilder();
                sheetContent.append("工作表: ").append(sheet.getSheetName()).append("\n");

                for (Row row : sheet) {
                    StringBuilder rowContent = new StringBuilder();
                    for (Cell cell : row) {
                        String cellValue = getCellValue(cell);
                        if (!cellValue.isBlank()) {
                            rowContent.append(cellValue).append(" | ");
                        }
                    }
                    if (!rowContent.isEmpty()) {
                        sheetContent.append(rowContent).append("\n");
                    }
                }

                Document doc = new Document(sheetContent.toString().trim());
                doc.getMetadata().put("source", resource.getFilename());
                doc.getMetadata().put("format", "excel");
                doc.getMetadata().put("sheet", sheet.getSheetName());
                documents.add(doc);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取 Excel 文件失败: " + e.getMessage(), e);
        }
        return documents;
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }
}
