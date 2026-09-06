package com.pulseos.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DocumentConverterEngine {

    /** Extracts full plain text from a local PDF completely offline. */
    public String extractTextFromPdf(Path pdfPath) throws IOException {
        File file = pdfPath.toFile();
        if (!file.exists()) throw new IOException("PDF file does not exist at: " + pdfPath);

        try (PDDocument document = Loader.loadPDF(file)) {
            return new PDFTextStripper().getText(document);
        }
    }

    public String getPdfSummary(Path pdfPath) throws IOException {
        File file = pdfPath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            int pageCount = document.getNumberOfPages();
            boolean isEncrypted = document.isEncrypted();
            return String.format("Pages: %d | Encrypted: %b | Size: %.2f KB",
                    pageCount, isEncrypted, file.length() / 1024.0);
        }
    }

    /** DOCX -> plain text, fully offline via Apache POI. */
    public String docxToText(Path docxPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(docxPath.toFile());
             XWPFDocument doc = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    /** PPTX -> plain text (all slide text runs), fully offline via Apache POI. */
    public String pptxToText(Path pptxPath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(pptxPath.toFile());
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("--- Slide ").append(slideNum++).append(" ---\n");
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        sb.append(textShape.getText()).append("\n");
                    }
                }
            }
        }
        return sb.toString();
    }

    /** XLSX -> CSV (first sheet), fully offline via Apache POI. */
    public void xlsxToCsv(Path xlsxPath, Path csvTarget) throws IOException {
        try (FileInputStream fis = new FileInputStream(xlsxPath.toFile());
             XSSFWorkbook workbook = new XSSFWorkbook(fis);
             FileWriter writer = new FileWriter(csvTarget.toFile())) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            for (var row : sheet) {
                StringBuilder line = new StringBuilder();
                for (var cell : row) {
                    if (line.length() > 0) line.append(",");
                    line.append(cell.toString().replace(",", " "));
                }
                writer.write(line.toString());
                writer.write(System.lineSeparator());
            }
        }
    }

    /** Plain TXT -> simple PDF, fully offline via PDFBox. Real conversion, not a stub. */
    public void textToPdf(Path txtSource, Path pdfTarget) throws IOException {
        String content = Files.readString(txtSource);
        String[] lines = content.split("\\r?\\n");

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont font = new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(font, 11);
            contentStream.beginText();
            contentStream.setLeading(14.5f);
            contentStream.newLineAtOffset(40, 800);

            int linesOnPage = 0;
            for (String line : lines) {
                if (linesOnPage > 50) {
                    contentStream.endText();
                    contentStream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(font, 11);
                    contentStream.beginText();
                    contentStream.setLeading(14.5f);
                    contentStream.newLineAtOffset(40, 800);
                    linesOnPage = 0;
                }
                contentStream.showText(line.length() > 100 ? line.substring(0, 100) : line);
                contentStream.newLine();
                linesOnPage++;
            }
            contentStream.endText();
            contentStream.close();

            document.save(pdfTarget.toFile());
        }
    }
}
