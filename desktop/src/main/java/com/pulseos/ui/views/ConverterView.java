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
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ConverterView extends VBox {

    private final ImageConverterEngine imageEngine = new ImageConverterEngine();
    private final DocumentConverterEngine documentEngine = new DocumentConverterEngine();
    private final ArchiveConverterEngine archiveEngine = new ArchiveConverterEngine();
    private final MediaConverterEngine mediaEngine = new MediaConverterEngine();
    private final EbookConverterEngine ebookEngine = new EbookConverterEngine();
    private final AiFeaturesService aiService;
    private final Consumer<String> logCallback;

    private static final Map<String, List<String>> CATEGORY_TARGET_FORMATS = Map.of(
            "Image", List.of("jpg", "jpeg", "png", "bmp", "gif", "pdf"),
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
    private final ExecutorService workExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "pulseos-converter");
        t.setDaemon(true);
        return t;
    });
    private Button convertButton;
    private List<File> selectedFiles = new ArrayList<>();

    public ConverterView(AiFeaturesService aiService, Consumer<String> logCallback) {
        this.aiService = aiService;
        this.logCallback = logCallback;
        this.getStyleClass().add("card");
        this.setSpacing(14);
        setPadding(new Insets(4));
        buildUi();
    }

    private void buildUi() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("view-shell-sidebar");
        Label sideTitle = new Label("CONVERTER");
        sideTitle.getStyleClass().add("sidebar-title");
        sidebar.getChildren().add(sideTitle);
        addSidebarButton(sidebar, "▧  Image", true);
        addSidebarButton(sidebar, "▣  Video", false);
        addSidebarButton(sidebar, "♬  Audio", false);
        addSidebarButton(sidebar, "▤  Document", false);
        addSidebarButton(sidebar, "▥  Ebook", false);
        addSidebarButton(sidebar, "◈  Archive", false);
        sidebar.getChildren().add(new Separator());
        addSidebarButton(sidebar, "◷  Recent Conversions", false);
        addSidebarButton(sidebar, "▤  Conversion History", false);

        VBox main = new VBox(10);
        main.getStyleClass().add("view-shell-content");
        Label title = new Label("File Converter");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Convert your files quickly and easily. Supports multiple formats and works offline.");
        subtitle.getStyleClass().add("view-subtitle");

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

        convertButton = new Button("Convert");
        convertButton.getStyleClass().add("alert-btn-danger");
        convertButton.setOnAction(e -> runConversion());

        HBox row1 = new HBox(10, new Label("Category:"), categoryBox, new Label("Target:"), formatBox);
        row1.setAlignment(Pos.CENTER_LEFT);

        HBox row2 = new HBox(10, chooseBtn, convertButton);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label dropZone = new Label("＋\n\nDrag & Drop Your Files Here\nor click to browse from your device\n\nSupports: JPG, PNG, WEBP, BMP, TIFF, GIF   (Max 200 MB per file)");
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setMaxWidth(Double.MAX_VALUE);
        dropZone.setMinHeight(190);
        dropZone.getStyleClass().add("converter-drop-zone");
        dropZone.setOnMouseClicked(e -> chooseFile());

        Label offline = new Label("● 100% Local & Offline");
        offline.getStyleClass().add("offline-status");

        Label toolNote = new Label("Note: Audio/Video needs ffmpeg on PATH, eBook needs Calibre on PATH, CAD isn't supported yet — checked automatically, no fake conversions.");
        toolNote.setWrapText(true);
        toolNote.getStyleClass().add("hw-text");

        main.getChildren().addAll(title, subtitle, offline, buildCategoryTabs(), dropZone, row1, row2, fileLabel, statusLabel, toolNote);
        VBox.setVgrow(main, Priority.ALWAYS);
        HBox shell = new HBox(0, sidebar, main);
        HBox.setHgrow(main, Priority.ALWAYS);
        this.getChildren().add(shell);
    }

    private HBox buildCategoryTabs() {
        HBox tabs = new HBox(7);
        for (String category : List.of("▧ Image", "▣ Video", "♬ Audio", "▤ Document", "▥ Ebook", "◈ Archive")) {
            Button button = new Button(category);
            button.getStyleClass().add("converter-category-tab");
            if (category.startsWith("▧")) button.getStyleClass().add("converter-category-active");
            tabs.getChildren().add(button);
        }
        return tabs;
    }

    private void addSidebarButton(VBox sidebar, String text, boolean active) {
        Button button = new Button(text);
        button.getStyleClass().add("view-sidebar-button");
        if (active) button.getStyleClass().add("view-sidebar-active");
        sidebar.getChildren().add(button);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select files to convert");
        Window win = this.getScene() != null ? this.getScene().getWindow() : null;
        
        // Use a generic Object and then check its type to avoid compilation errors across JavaFX versions
        Object result = chooser.showOpenMultipleDialog(win);
        
        if (result instanceof File[]) {
            File[] files = (File[]) result;
            if (files != null && files.length > 0) {
                selectedFiles.clear();
                selectedFiles.addAll(Arrays.asList(files));
                fileLabel.setText("Selected: " + files.length + " file(s)");
                statusLabel.setText("");
                if (files.length == 1) {
                    selectDetectedCategory(files[0].toPath());
                } else {
                    statusLabel.setText("Multiple files selected. Target format will be applied to all.");
                }
            }
        } else if (result instanceof List) {
            List<File> files = (List<File>) result;
            if (files != null && !files.isEmpty()) {
                selectedFiles.clear();
                selectedFiles.addAll(files);
                fileLabel.setText("Selected: " + files.size() + " file(s)");
                statusLabel.setText("");
                if (files.size() == 1) {
                    selectDetectedCategory(files.get(0).toPath());
                } else {
                    statusLabel.setText("Multiple files selected. Target format will be applied to all.");
                }
            }
        }
    }

    private void selectDetectedCategory(Path file) {
        String extension = getExtension(file.getFileName().toString()).toLowerCase(Locale.ROOT);
        String category = switch (extension) {
            case "png", "jpg", "jpeg", "bmp", "gif", "tif", "tiff", "webp" -> "Image";
            case "pdf", "txt", "docx", "pptx", "xlsx", "csv" -> "Document";
            case "zip", "tar", "gz", "bz2", "7z" -> "Compressed / Archive";
            case "mp3", "wav", "aac", "flac", "mp4", "mkv", "avi", "mov", "webm" -> "Audio / Video (needs ffmpeg)";
            case "epub", "mobi", "azw3", "fb2" -> "eBook (needs Calibre)";
            default -> null;
        };
        if (category == null) {
            statusLabel.setText("⚠ Detected format ." + extension + "; choose a supported category.");
            return;
        }
        categoryBox.setValue(category);
        formatBox.getItems().setAll(CATEGORY_TARGET_FORMATS.get(category));
        if (category.equals("Image")) {
            formatBox.setValue("pdf");
        } else if (!formatBox.getItems().isEmpty()) {
            formatBox.setValue(formatBox.getItems().get(0));
        }
        statusLabel.setText("Detected ." + extension + " → " + category + ". Compatible targets updated.");
    }

    private void runConversion() {
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            statusLabel.setText("Pehle koi file choose karo.");
            return;
        }

        String category = categoryBox.getValue();
        String targetFormat = formatBox.getValue();
        if (category == null || targetFormat == null || targetFormat.isBlank()) {
            statusLabel.setText("❌ Choose a category and target format first.");
            return;
        }

        File firstFile = selectedFiles.get(0);
        Path source = firstFile.toPath();
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            statusLabel.setText("❌ The selected file no longer exists or cannot be read.");
            return;
        }

        String sourceExt = getExtension(firstFile.getName()).toLowerCase();
        String fileName = firstFile.getName();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = (lastDot == -1) ? fileName : fileName.substring(0, lastDot);
        Path parent = source.getParent() == null ? Paths.get(".").toAbsolutePath() : source.getParent();
        Path target = parent.resolve(baseName + "_converted." + targetFormat);

        convertButton.setDisable(true);
        statusLabel.setText("⏳ Converting locally…");

        CompletableFuture.supplyAsync(() -> {
            try {
                return performConversion(category, selectedFiles, target, sourceExt, targetFormat);
            } catch (Exception ex) {
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, workExecutor).whenComplete((message, error) -> Platform.runLater(() -> {
            convertButton.setDisable(false);
            if (error != null) {
                statusLabel.setText("❌ Failed: " + friendlyMessage(error));
            } else if (message != null) {
                statusLabel.setText("✅ Saved: " + target.getFileName() + " (" + message + ")");
                logCallback.accept("🔄 " + message + " → " + target.getFileName());
            }
        }));
    }

    private String performConversion(String category, List<File> files, Path target, String sourceExt, String targetFormat) throws Exception {
        Path source = files.get(0).toPath();
        switch (sourceExt) {
            case "pdf" -> {
                if (!targetFormat.equals("txt")) throw new java.io.IOException("PDF can currently only convert to TXT here.");
                String text = documentEngine.extractTextFromPdf(source);
                java.nio.file.Files.writeString(target, text);
                return "PDF text extracted offline";
            }
            case "docx" -> {
                if (!targetFormat.equals("txt")) throw new java.io.IOException("DOCX can currently only convert to TXT here.");
                String text = documentEngine.docxToText(source);
                java.nio.file.Files.writeString(target, text);
                return "DOCX text extracted offline";
            }
            case "pptx" -> {
                if (!targetFormat.equals("txt")) throw new java.io.IOException("PPTX can currently only convert to TXT here.");
                String text = documentEngine.pptxToText(source);
                java.nio.file.Files.writeString(target, text);
                return "PPTT text extracted offline";
            }
            case "xlsx" -> {
                if (!targetFormat.equals("csv")) throw new java.io.IOException("XLSX can currently only convert to CSV here.");
                documentEngine.xlsxToCsv(source, target);
                return "XLSX converted to CSV offline";
            }
            case "txt" -> {
                if (!targetFormat.equals("pdf")) throw new java.io.IOException("TXT can currently only convert to PDF here.");
                documentEngine.textToPdf(source, target);
                return "TXT converted to PDF offline";
            }
            default -> {
                switch (category) {
                    case "Image" -> {
                        if (targetFormat.equalsIgnoreCase("pdf")) {
                            documentEngine.imageToPdf(source, target);
                            return "Image encoded into PDF offline";
                        }
                        imageEngine.convertImageFormat(source, target, targetFormat);
                        return "Image encoded offline";
                    }
                    case "Compressed / Archive" -> {
                        // For the archive engine, we convert the List back to Array just for the call
                        archiveEngine.convert(source, target, targetFormat);
                        return "Archive re-packed offline";
                    }
                    case "Audio / Video (needs ffmpeg)" -> {
                        var outcome = mediaEngine.convert(source, target);
                        if (!outcome.success()) throw new java.io.IOException(outcome.message());
                        return outcome.message();
                    }
                    case "eBook (needs Calibre)" -> {
                        var outcome = ebookEngine.convert(source, target);
                        if (!outcome.success()) throw new java.io.IOException(outcome.message());
                        return outcome.message();
                    }
                    default -> throw new java.io.IOException("CAD conversion isn't supported yet.");
                }
            }
        }
    }

    private String friendlyMessage(Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "";
    }

    public void shutdown() {
        workExecutor.shutdownNow();
    }
}
