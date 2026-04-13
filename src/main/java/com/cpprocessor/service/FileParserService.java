package com.cpprocessor.service;

import com.cpprocessor.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class FileParserService {

    private static final Set<String> SPREADSHEET_EXTENSIONS = Set.of(".xlsx", ".xls");
    private static final Set<String> WORD_EXTENSIONS = Set.of(".docx");
    private static final Set<String> PDF_EXTENSIONS = Set.of(".pdf");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".csv", ".txt");
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(".zip");

    private static final long MAX_ENTRY_SIZE = 10 * 1024 * 1024;

    private static final Set<String> PROCESSING_TASK_HEADERS = Set.of(
            "id", "file_name", "status", "extracted_data", "error", "created_at", "updated_at"
    );

    public String parse(byte[] content, String fileName) {
        String ext = FileUtils.getExtension(fileName);
        log.info("Parsing file: {} (extension: {})", fileName, ext);

        try {
            if (SPREADSHEET_EXTENSIONS.contains(ext)) {
                return parseSpreadsheet(content);
            } else if (WORD_EXTENSIONS.contains(ext)) {
                return parseDocx(content);
            } else if (PDF_EXTENSIONS.contains(ext)) {
                return parsePdf(content);
            } else if (TEXT_EXTENSIONS.contains(ext)) {
                return new String(content, StandardCharsets.UTF_8);
            } else if (ARCHIVE_EXTENSIONS.contains(ext)) {
                return parseZip(content);
            } else {
                log.warn("Unknown extension {}, trying as plain text", ext);
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to parse file {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Failed to parse file: " + fileName, e);
        }
    }

    public boolean isStructuredProcessingTasksExcel(byte[] content, String fileName) {
        String ext = FileUtils.getExtension(fileName);
        if (!SPREADSHEET_EXTENSIONS.contains(ext)) {
            return false;
        }

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                return false;
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return false;
            }

            DataFormatter formatter = new DataFormatter();
            Set<String> headers = readHeaders(headerRow, formatter);

            log.info("Detected excel headers: {}", headers);
            return headers.containsAll(PROCESSING_TASK_HEADERS);
        } catch (Exception e) {
            log.warn("Failed to inspect excel structure for {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    public List<Map<String, String>> parseStructuredProcessingTasksExcel(byte[] content) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }

            Map<String, Integer> headerMap = new LinkedHashMap<>();
            short lastCellNum = headerRow.getLastCellNum();
            if (lastCellNum < 0) {
                return List.of();
            }

            for (int i = 0; i < lastCellNum; i++) {
                Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) {
                    continue;
                }

                String header = normalizeHeader(formatter.formatCellValue(cell, evaluator));
                if (!header.isBlank()) {
                    headerMap.put(header, i);
                }
            }

            List<Map<String, String>> result = new ArrayList<>();

            for (int rowIdx = headerRow.getRowNum() + 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (isRowEmpty(row, formatter, evaluator)) {
                    continue;
                }

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                    Cell cell = row.getCell(entry.getValue(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : normalizeCellValue(formatter.formatCellValue(cell, evaluator));
                    rowMap.put(entry.getKey(), value);
                }

                result.add(rowMap);
            }

            log.info("Parsed {} rows from structured processing tasks excel", result.size());
            return result;
        }
    }

    private String parseSpreadsheet(byte[] content) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);

                if (workbook.getNumberOfSheets() > 1) {
                    sb.append("=== Лист: ").append(sheet.getSheetName()).append(" ===\n");
                }

                for (Row row : sheet) {
                    short lastCellNum = row.getLastCellNum();
                    if (lastCellNum < 0) {
                        continue;
                    }

                    List<String> cells = new ArrayList<>();
                    for (int i = 0; i < lastCellNum; i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String value = cell == null ? "" : normalizeCellValue(formatter.formatCellValue(cell, evaluator));
                        cells.add(value);
                    }

                    if (cells.stream().allMatch(String::isBlank)) {
                        continue;
                    }

                    sb.append(String.join(" | ", cells)).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private Set<String> readHeaders(Row headerRow, DataFormatter formatter) {
        Set<String> headers = new HashSet<>();
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum < 0) {
            return headers;
        }

        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }

            String value = normalizeHeader(formatter.formatCellValue(cell));
            if (!value.isBlank()) {
                headers.add(value);
            }
        }

        return headers;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }

        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return true;
        }

        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }

            String value = normalizeCellValue(formatter.formatCellValue(cell, evaluator));
            if (!value.isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_");
    }

    private String normalizeCellValue(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String parseDocx(byte[] content) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }

            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(cell.getText().trim());
                    }
                    sb.append(String.join(" | ", cells)).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String parsePdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseZip(byte[] content) throws IOException {
        StringBuilder sb = new StringBuilder();

        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(content))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                if (entry.getSize() > MAX_ENTRY_SIZE) {
                    log.warn("Skipping large entry: {} ({} bytes)", entry.getName(), entry.getSize());
                    continue;
                }

                String entryName = entry.getName();

                if (entryName.startsWith("__MACOSX") || entryName.startsWith(".")) {
                    continue;
                }

                byte[] entryContent = zis.readAllBytes();
                sb.append("=== ").append(entryName).append(" ===\n");

                try {
                    String parsed = parse(entryContent, entryName);
                    sb.append(parsed).append("\n");
                } catch (Exception e) {
                    log.warn("Could not parse entry {}: {}", entryName, e.getMessage());
                    sb.append("[не удалось распарсить]\n");
                }
            }
        }

        return sb.toString();
    }
}
