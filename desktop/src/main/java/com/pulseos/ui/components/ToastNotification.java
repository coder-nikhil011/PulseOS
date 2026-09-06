package com.pulseos.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public class ToastNotification {

    public enum Type {
        SUCCESS, INFO, ERROR
    }

    public static void show(StackPane rootPane, String message, Type type) {
        HBox toast = new HBox();
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setMaxWidth(380);
        toast.setMaxHeight(50);
        toast.getStyleClass().addAll("toast-notification", "toast-" + type.name().toLowerCase());

        Label label = new Label(message);
        label.getStyleClass().add("toast-text");
        toast.getChildren().add(label);

        // Position at Top Right corner of the screen
        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        toast.setTranslateX(-20);
        toast.setTranslateY(20);
        toast.setOpacity(0.0);

        rootPane.getChildren().add(toast);

        // Entrance Animation (Fade In)
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        // Screen Display Holding Time
        PauseTransition stayOnScreen = new PauseTransition(Duration.seconds(3.5));

        // Exit Animation (Fade Out)
        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toast);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        SequentialTransition sequence = new SequentialTransition(fadeIn, stayOnScreen, fadeOut);
        sequence.setOnFinished(e -> rootPane.getChildren().remove(toast));
        sequence.play();
    }
}