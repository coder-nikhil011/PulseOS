package com.pulseos;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.pulseos.ui.MainDashboardController;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_dashboard.fxml"));
            Parent root = loader.load();
            MainDashboardController controller = loader.getController();

            primaryStage.setTitle("PulseOS — Device Health & Healing Center");
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(650);
            primaryStage.setResizable(true);

            // Let JavaFX calculate the real screen position. This is more reliable on
            // macOS than manually applying visual-bound coordinates (especially with
            // Retina scaling, menu bars, docks, or multiple displays).
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1500, Math.max(1100, bounds.getWidth() - 80));
            double height = Math.min(950, Math.max(700, bounds.getHeight() - 100));

            Scene scene = new Scene(root, width, height);
            var css = getClass().getResource("/styles/dark_theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(e -> controller.stopServices());

            // Show first, then position/focus. On macOS this avoids a JavaFX window
            // being created behind Terminal/IDE or ending up outside the visible area.
            primaryStage.setIconified(false);
            primaryStage.show();
            primaryStage.centerOnScreen();
            primaryStage.setIconified(false);
            primaryStage.toFront();
            primaryStage.requestFocus();

            // Keep the window in front briefly while macOS activates the JavaFX app.
            // Then return to normal window behavior.
            primaryStage.setAlwaysOnTop(true);
            PauseTransition focusBoost = new PauseTransition(Duration.seconds(1.5));
            focusBoost.setOnFinished(e -> {
                primaryStage.setAlwaysOnTop(false);
                primaryStage.toFront();
                primaryStage.requestFocus();
            });
            focusBoost.play();

            // If the display is smaller than the requested minimum, maximize rather
            // than creating a clipped/off-screen window.
            if (bounds.getWidth() < 1150 || bounds.getHeight() < 760) {
                primaryStage.setMaximized(true);
            }
        } catch (Throwable e) {
            // Never fail silently. If FXML/controller initialization breaks, put an
            // emergency visible JavaFX window on screen so the actual exception is
            // readable instead of making the app appear to do nothing.
            e.printStackTrace();
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Label message = new Label("PulseOS could not load the dashboard\n\n" + sw);
                message.setWrapText(true);
                StackPane pane = new StackPane(message);
                Scene errorScene = new Scene(pane, 1000, 700);
                primaryStage.setTitle("PulseOS — Startup Error");
                primaryStage.setScene(errorScene);
                primaryStage.setAlwaysOnTop(true);
                primaryStage.show();
                primaryStage.centerOnScreen();
                primaryStage.toFront();
                primaryStage.requestFocus();
                Platform.runLater(() -> primaryStage.setAlwaysOnTop(false));
            } catch (Throwable ignored) {
                // JavaFX startup itself failed; the original stack trace remains in terminal.
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
