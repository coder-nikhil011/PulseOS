package com.pulseos.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SettingsView extends VBox {

    public SettingsView(boolean autoOrganizeEnabled, Consumer<Boolean> onAutoOrganizeToggle,
                         boolean protectActiveProjects, Consumer<Boolean> onProtectToggle) {
        this.getStyleClass().add("card");
        this.setSpacing(14);
        setPadding(new Insets(4));

        Label title = new Label("⚙ SETTINGS");
        title.getStyleClass().add("card-header-title");

        CheckBox autoOrganize = new CheckBox("Auto-organize new Downloads into subfolders");
        autoOrganize.setSelected(autoOrganizeEnabled);
        autoOrganize.setOnAction(e -> onAutoOrganizeToggle.accept(autoOrganize.isSelected()));

        CheckBox protectActive = new CheckBox("Never touch build artifacts of active projects (recommended)");
        protectActive.setSelected(protectActiveProjects);
        protectActive.setOnAction(e -> onProtectToggle.accept(protectActive.isSelected()));

        Label quarantineNote = new Label(
                "Cleaned folders are moved to ~/.pulseos/quarantine and permanently deleted after 7 days, giving you time to undo.");
        quarantineNote.setWrapText(true);
        quarantineNote.getStyleClass().add("hw-text");

        this.getChildren().addAll(title, autoOrganize, protectActive, quarantineNote);
    }
}
