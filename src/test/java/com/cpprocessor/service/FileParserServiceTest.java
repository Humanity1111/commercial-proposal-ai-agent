package com.cpprocessor.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FileParserServiceTest {

    private FileParserService fileParserService;

    @BeforeEach
    void setUp() {
        fileParserService = new FileParserService();
    }

    @Test
    void parseCsv_ReturnsText() {
        String csv = "Товар;Количество;Цена\nБолты М10;100;15.5";
        String result = fileParserService.parse(csv.getBytes(StandardCharsets.UTF_8), "data.csv");

        assertTrue(result.contains("Болты М10"));
        assertTrue(result.contains("100"));
    }

    @Test
    void parseXlsx_ExtractsRows() throws IOException {
        byte[] xlsx = createTestXlsx();
        String result = fileParserService.parse(xlsx, "proposal.xlsx");

        assertTrue(result.contains("Болты М10"));
        assertTrue(result.contains("100"));
        assertTrue(result.contains("15.5"));
    }

    @Test
    void parseDocx_ExtractsParagraphsAndTables() throws IOException {
        byte[] docx = createTestDocx();
        String result = fileParserService.parse(docx, "proposal.docx");

        assertTrue(result.contains("Коммерческое предложение"));
        assertTrue(result.contains("Болты М10"));
    }

    @Test
    void parsePdf_ExtractsText() throws IOException {
        byte[] pdf = createTestPdf();
        String result = fileParserService.parse(pdf, "proposal.pdf");

        assertTrue(result.contains("Hello PDF"));
    }

    @Test
    void parseZip_ExtractsContainedFiles() throws IOException {
        byte[] zip = createTestZip();
        String result = fileParserService.parse(zip, "archive.zip");

        assertTrue(result.contains("data.csv"));
        assertTrue(result.contains("Болты"));
    }

    @Test
    void parseUnknownExtension_FallsBackToText() {
        String text = "some plain text content";
        String result = fileParserService.parse(text.getBytes(StandardCharsets.UTF_8), "file.log");

        assertEquals(text, result);
    }

    @Test
    void parseXlsx_EmptySheet_ReturnsEmpty() throws IOException {
        byte[] xlsx = createEmptyXlsx();
        String result = fileParserService.parse(xlsx, "empty.xlsx");

        assertTrue(result.isBlank());
    }

    // --- хелперы для генерации тестовых файлов ---

    private byte[] createTestXlsx() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("КП");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Товар");
            header.createCell(1).setCellValue("Количество");
            header.createCell(2).setCellValue("Цена");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Болты М10");
            row1.createCell(1).setCellValue(100);
            row1.createCell(2).setCellValue(15.5);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createEmptyXlsx() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            wb.createSheet("Empty");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createTestDocx() throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Коммерческое предложение");

            XWPFTable table = doc.createTable();
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Товар");
            headerRow.addNewTableCell().setText("Кол-во");

            XWPFTableRow dataRow = table.createRow();
            dataRow.getCell(0).setText("Болты М10");
            dataRow.getCell(1).setText("100");

            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createTestPdf() throws IOException {
        try (PDDocument document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Hello PDF");
                cs.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createTestZip() throws IOException {
        try (var out = new ByteArrayOutputStream();
             var zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("data.csv"));
            zos.write("Товар;Кол-во\nБолты;50".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            return out.toByteArray();
        }
    }
}
