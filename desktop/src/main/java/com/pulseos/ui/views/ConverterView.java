package com.pulseos.ui.views;

import com.pulseos.ai.AiFeaturesService;
import com.pulseos.converter.ArchiveConverterEngine;
import com.pulseos.converter.DocumentConverterEngine;
import com.pulseos.converter.EbookConverterEngine;
import com.pulseos.converter.ImageConverterEngine;
import com.pulseos.converter.MediaConverterEngine;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Problem 4, expanded: pick a category, pick a target format, pick a file, convert.
 * Image + Archive + most Document paths are pure-Java and always work offline.
 * Audio/Video and eBook shell out to a local ffmpeg / Calibre install if present —
 * still 100% offline/private, just honestly dependent on that tool existing.
 * CAD (DWG/DXF) has no safe pure-Java or common CLI path, so it's marked unsupported
 * instead of faking a broken conversion.
 */
public class ConverterView extends VBox {

    private final ImageConverterEngine imageEngine = new ImageConverterEngine();
    private final DocumentConverterEngine documentEngine = new DocumentConverterEngine();
    private final ArchiveConverterEngine archiveEngine = new ArchiveConverterEngine();
    private final MediaConverterEngine mediaEngine = new MediaConverterEngine();
    private final EbookConverterEngine ebookEngine = new EbookConverterEngine();
    private final AiFeaturesService aiService;
    private final Consumer<String> logCallback;

    private static final Map<String, List<String>> CATEGORY_TARGET_FORMATS = Map.of(
            "Image", List.of("png", "jpg", "bmp", "gif"),
            "Document", List.of("txt", "csv", "pdf"),
            "Compressed / Archive", List.of("zip", "tar", "tar.gz", "tar.bz2", "7z"),
            "Audio / Video (needs ffmpeg)", List.of("mp3", "wav", "aac", "flac", "mp4", "mkv", "avi", "mov", "webm"),
            "eBook (needs Calibre)", List.of("epub", "mobi", "azw3", "pdf", "fb2"),
            "CAD (not supported yet)", List.of()
    );

    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final ComboBox<String> formatBox = new ComboBox<>();
    private final Label fileLabel = new Label("Koi file select nahi hui.");
    private final Label statusLabel = new Label("");
    private final TextField searchField = new TextField();
    private final ListView<Path> searchResults = new ListView<>();
    private final Label searchStatusLabel = new Label("");
    private File selectedFile;

    public ConverterView(AiFeaturesService aiService, Consumer<String> logCallback) {
        this.aiService = aiService;
        this.logCallback = logCallback;
        this.getStyleClass().add("card");
        this.setSpacing(14);
        setPadding(new Insets(4));
        buildUi();
    }

    private void buildUi() {
        Label title = new Label("🔄 CONVERTER — 100% Offline, Nothing Ever Uploaded");
        title.getStyleClass().add("card-header-title");

        categoryBox.getItems().addAll(CATEGORY_TARGET_FORMATS.keySet());
        categoryBox.setValue("Image");
        categoryBox.setOnAction(e -> {
            formatBox.getItems().setAll(CATEGORY_TARGET_FORMATS.get(categoryBox.getValue()));
            if (!formatBox.getItems().isEmpty()) formatBox.setValue(formatBox.getItems().get(0));
        });
        formatBox.getItems().setAll(CATEGORY_TARGET_FORMATS.get("Image"));
        formatBox.setValue("png");

        Button chooseBtn = new Button("Choose File");
        chooseBtn.setOnAction(e -> chooseFile());

        Button convertBtn = new Button("Convert");
        convertBtn.getStyleClass().add("alert-btn-danger");
        convertBtn.setOnAction(e -> runConversion());

        HBox row1 = new HBox(10, new Label("Category:"), categoryBox, new Label("Target:"), formatBox);
        row1.setAlignment(Pos.CENTER_LEFT);

        HBox row2 = new HBox(10, chooseBtn, convertBtn);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label toolNote = new Label(
                "Note: Audio/Video needs ffmpeg on PATH, eBook needs Calibre on PATH, CAD isn't supported yet — checked automatically, no fake conversions.");
        toolNote.setWrapText(true);
        toolNote.getStyleClass().add("hw-text");

        this.getChildren().addAll(title, buildAiSearchSection(), new Separator(),
                row1, row2, fileLabel, statusLabel, toolNote);
    }

    /** AI Feature 3: Natural Language File Search — "find that electricity bill from last month". */
    private VBox buildAiSearchSection() {
        Label heading = new Label("🤖 AI Search — describe the file in plain English");
        heading.getStyleClass().add("stat-caption-bold");

        searchField.setPromptText("e.g. \"electricity bill pdf from last month\"");
        Button searchBtn = new Button("🔎 Search");
        searchBtn.setOnAction(e -> runAiSearch());

        HBox searchRow = new HBox(8, searchField, searchBtn);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        searchResults.setPrefHeight(90);
        searchResults.setPlaceholder(new Label("Search results yahan dikhenge — result par click karke seedha convert karo."));
        searchResults.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
        searchResults.setOnMouseClicked(e -> {
            Path picked = searchResults.getSelectionModel().getSelectedItem();
            if (picked != null) {
                selectedFile = picked.toFile();
                fileLabel.setText("Selected (from AI search): " + picked.getFileName());
                statusLabel.setText("");
            }
        });

        searchStatusLabel.getStyleClass().add("hw-text");

        return new VBox(6, heading, searchRow, searchStatusLabel, searchResults);
    }

    private void runAiSearch() {
        String query = searchField.getText();
        if (query == null || query.isBlank()) return;

        searchStatusLabel.setText("🤖 Understanding your request...");
        searchResults.getItems().clear();

        aiService.parseSearchIntent(query).thenAccept(intent -> {
            List<Path> matches = searchFileSystem(intent.keywords(), intent.targetFormat());
            Platform.runLater(() -> {
                searchResults.getItems().setAll(matches);
                searchStatusLabel.setText(matches.isEmpty()
                        ? "Kuch nahi mila keywords [" + intent.keywords() + "]" + (intent.targetFormat() != null ? " ." + intent.targetFormat() : "") + " ke liye."
                        : matches.size() + " result(s) found for [" + intent.keywords() + "]"
                          + (intent.targetFormat() != null ? " ." + intent.targetFormat() : ""));
            });
        });
    }

    /** Shallow-ish keyword filename search under the user's home (Documents/Downloads/Desktop) — fast enough for a live UI. */
    private List<Path> searchFileSystem(String keywords, String targetFormat) {
        List<String> terms = List.of(keywords.toLowerCase().split("\\s+"));
        Path home = Paths.get(System.getProperty("user.home"));
        List<Path> results = new java.util.ArrayList<>();

        for (String sub : List.of("Documents", "Downloads", "Desktop")) {
            Path folder = home.resolve(sub);
            if (!Files.isDirectory(folder)) continue;
            try (var stream = Files.walk(folder, 4)) {
                results.addAll(stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            boolean matchesFormat = targetFormat == null || name.endsWith("." + targetFormat.toLowerCase());
                            boolean matchesKeywords = terms.isEmpty() || terms.stream().anyMatch(name::contains);
                            return matchesFormat && matchesKeywords;
                        })
                        .limit(20)
                        .collect(Collectors.toList()));
            } catch (Exception ignored) {
            }
        }
        return results.stream().limit(20).collect(Collectors.toList());
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a file to convert");
        Window window = this.getScene() != null ? this.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            selectedFile = file;
            fileLabel.setText("Selected: " + file.getName());
            statusLabel.setText("");
        }
    }

    private void runConversion() {
        if (selectedFile == null) {
            statusLabel.setText("Pehle koi file choose karo.");
            return;
        }

        String category = categoryBox.getValue();
        String targetFormat = formatBox.getValue();
        Path source = selectedFile.toPath();
        String sourceExt = getExtension(selectedFile.getName()).toLowerCase();
        String baseName = selectedFile.getName().replaceAll("\\.[^.]+$", "");
        Path target = source.getParent().resolve(baseName + "_converted." + targetFormat);

        try {
            switch (category) {
                case "Image" -> {
                    imageEngine.convertImageFormat(source, target, targetFormat);
                    succeed(target, "Image converted offline");
                }
                case "Document" -> convertDocument(source, target, sourceExt, targetFormat);
                case "Compressed / Archive" -> {
                    archiveEngine.convert(source, target, targetFormat);
                    succeed(target, "Archive re-packed offline");
                }
                case "Audio / Video (needs ffmpeg)" -> {
                    var outcome = mediaEngine.convert(source, target);
                    if (outcome.success()) succeed(target, outcome.message());
                    else statusLabel.setText("❌ " + outcome.message());
                }
                case "eBook (needs Calibre)" -> {
                    var outcome = ebookEngine.convert(source, target);
                    if (outcome.success()) succeed(target, outcome.message());
                    else statusLabel.setText("❌ " + outcome.message());
                }
                default -> statusLabel.setText("❌ CAD conversion isn't supported yet (no safe offline engine exists).");
            }
        } catch (Exception ex) {
            statusLabel.setText("❌ Failed: " + ex.getMessage());
        }
    }

    private void convertDocument(Path source, Path target, String sourceExt, String targetFormat) throws Exception {
        switch (sourceExt) {
            case "pdf" -> {
                if (!targetFormat.equals("txt")) {
                    statusLabel.setText("❌ PDF can currently only convert to TXT here.");
                    return;
                }
                String text = documentEngine.extractTextFromPdf(source);
                java.nio.file.Files.writeString(target, text);
                succeed(target, "PDF text extracted offline");
            }
            case "docx" -> {
                if (!targetFormat.equals("txt")) {
                    statusLabel.setText("❌ DOCX can currently only convert to TXT here.");
                    return;
                }
                String text = documentEngine.docxToText(source);
                java.nio.file.Files.writeString(target, text);
                succeed(target, "DOCX text extracted offline");
            }
            case "pptx" -> {
                if (!targetFormat.equals("txt")) {
                    statusLabel.setText("❌ PPTX can currently only convert to TXT here.");
                    return;
                }
                String text = documentEngine.pptxToText(source);
                java.nio.file.Files.writeString(target, text);
                succeed(target, "PPTX text extracted offline");
            }
            case "xlsx" -> {
                if (!targetFormat.equals("csv")) {
                    statusLabel.setText("❌ XLSX can currently only convert to CSV here.");
                    return;
                }
                documentEngine.xlsxToCsv(source, target);
                succeed(target, "XLSX converted to CSV offline");
            }
            case "txt" -> {
                if (!targetFormat.equals("pdf")) {
                    statusLabel.setText("❌ TXT can currently only convert to PDF here.");
                    return;
                }
                documentEngine.textToPdf(source, target);
                succeed(target, "TXT converted to PDF offline");
            }
            default -> statusLabel.setText("❌ Unsupported source document type: ." + sourceExt);
        }
    }

    private void succeed(Path target, String reason) {
        statusLabel.setText("✅ Saved: " + target.getFileName() + " (" + reason + ")");
        logCallback.accept("🔄 " + reason + " → " + target.getFileName());
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }
}
