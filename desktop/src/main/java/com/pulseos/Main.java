package com.pulseos;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            WebEngine engine = webView.getEngine();
            engine.setOnError(event -> writeStartupError(new IllegalStateException("WebView error: " + event.getMessage())));
            DesktopTelemetryBridge telemetryBridge = new DesktopTelemetryBridge(primaryStage);
            telemetryBridge.start();
            engine.getLoadWorker().stateProperty().addListener((observable, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("pulseOSBridge", telemetryBridge);
                }
            });
            engine.load(getClass().getResource("/web/index.html").toExternalForm());

            primaryStage.setTitle("PulseOS — Device Health & Healing Center");
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            primaryStage.setResizable(true);

            // Let JavaFX calculate the real screen position. This is more reliable on
            // macOS than manually applying visual-bound coordinates (especially with
            // Retina scaling, menu bars, docks, or multiple displays).
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1322, Math.max(1100, bounds.getWidth() - 40));
            double height = Math.min(871, Math.max(700, bounds.getHeight() - 80));

            Scene scene = new Scene(webView, width, height);
            var theme = getClass().getResource("/styles/dark_theme.css");
            if (theme != null) {
                scene.getStylesheets().add(theme.toExternalForm());
            }
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> telemetryBridge.stop());
            primaryStage.show();
            primaryStage.centerOnScreen();
            primaryStage.toFront();
            primaryStage.requestFocus();
            primaryStage.setMaximized(false);
        } catch (Throwable e) {
            e.printStackTrace();
            writeStartupError(e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            primaryStage.setTitle("PulseOS — Startup Error");
            primaryStage.setScene(new Scene(new javafx.scene.control.Label("PulseOS could not load the dashboard\n\n" + sw), 1000, 700));
            primaryStage.show();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static void writeStartupError(Throwable error) {
        try {
            Path log = Path.of(System.getProperty("java.io.tmpdir"), "PulseOS-startup-error.log");
            StringWriter details = new StringWriter();
            error.printStackTrace(new PrintWriter(details));
            Files.writeString(log, details.toString());
        } catch (Exception ignored) {
            // Preserve the original startup failure if the log cannot be written.
        }
    }
}
