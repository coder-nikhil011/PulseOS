package com.pulseos.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;

public class SettingsView extends BorderPane {

    private final Consumer<Boolean> onAutoOrganizeToggle;
    private final Consumer<Boolean> onProtectToggle;
    private final StackPane contentArea = new StackPane();
    private final VBox sidebar = new VBox(5);
    private final Map<String, VBox> pages = new HashMap<>();

    public SettingsView(boolean autoOrganizeEnabled, Consumer<Boolean> onAutoOrganizeToggle,
                        boolean protectActiveProjects, Consumer<Boolean> onProtectToggle) {
        this.onAutoOrganizeToggle = onAutoOrganizeToggle;
        this.onProtectToggle = onProtectToggle;
        
        this.getStyleClass().add("settings-view");
        
        setupSidebar();
        setupContentArea();
        
        this.setLeft(sidebar);
        this.setCenter(contentArea);
        
        // Default page
        showPage("General");
    }

    private void setupSidebar() {
        sidebar.getStyleClass().add("view-shell-sidebar");
        sidebar.setPadding(new Insets(15, 10, 15, 10));
        
        Label title = new Label("SETTINGS");
        title.getStyleClass().add("sidebar-title");
        sidebar.getChildren().add(title);
        
        String[] menuItems = {"General", "System", "App Management", "Network", "Appearance", "About"};
        
        for (String item : menuItems) {
            Button btn = new Button(item);
            btn.getStyleClass().add("view-sidebar-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setOnAction(e -> showPage(item));
            sidebar.getChildren().add(btn);
        }
    }

    private void setupContentArea() {
        contentArea.getStyleClass().add("view-shell-content");
        contentArea.setPadding(new Insets(20));
        
        // Create all pages upfront
        pages.put("General", createGeneralPage());
        pages.put("System", createSystemPage());
        pages.put("App Management", createAppManagementPage());
        pages.put("Network", createNetworkPage());
        pages.put("Appearance", createAppearancePage());
        pages.put("About", createAboutPage());
    }

    private void showPage(String pageKey) {
        VBox page = pages.get(pageKey);
        if (page != null) {
            contentArea.getChildren().setAll(page);
            
            // Update sidebar active state
            sidebar.getChildren().forEach(node -> {
                if (node instanceof Button btn) {
                    if (btn.getText().equals(pageKey)) {
                        btn.getStyleClass().add("view-sidebar-active");
                    } else {
                        btn.getStyleClass().remove("view-sidebar-active");
                    }
                }
            });
        }
    }

    private VBox createGeneralPage() {
        CheckBox autoOrganize = new CheckBox("Launch on startup");
        autoOrganize.setSelected(true); // Default or from state
        autoOrganize.setOnAction(e -> onAutoOrganizeToggle.accept(autoOrganize.isSelected()));
        
        CheckBox protectActive = new CheckBox("Never touch build artifacts of active projects");
        protectActive.setSelected(true);
        protectActive.setOnAction(e -> onProtectToggle.accept(protectActive.isSelected()));
        
        return settingsCard("General Settings", 
            autoOrganize, 
            protectActive,
            toggleRow("Show in system tray", "Keep PulseOS available in the background", true),
            toggleRow("Automatic updates", "Check for updates automatically", true),
            selectRow("Update channel", "Choose how you want to receive updates", "Stable (Recommended)", "Beta"));
    }

    private VBox createSystemPage() {
        return settingsCard("System Settings",
            selectRow("Language", "Select your preferred language", "English (United States)"),
            selectRow("System font size", "Adjust text size across the application", "14px (Default)", "16px"),
            toggleRow("Start minimized", "Launch PulseOS in the system tray", false),
            toggleRow("Notifications", "Show system notifications", true));
    }

    private VBox createAppManagementPage() {
        return settingsCard("App Management", 
            new Label("Default apps                                      ›"),
            new Label("Installed apps                                ›"), 
            new Label("App permissions                         ›"),
            new Label("Auto-start apps                              ›"));
    }

    private VBox createNetworkPage() {
        return settingsCard("Network Settings",
            toggleRow("Network monitoring", "Allow PulseOS to probe network reachability", true),
            selectRow("DNS Provider", "Primary DNS for health checks", "Cloudflare (1.1.1.1)", "Google (8.8.8.8)"),
            new Label("Firewall status                              ● Enabled"));
    }

    private VBox createAppearancePage() {
        return settingsCard("Appearance",
            selectRow("Theme", "Choose your preferred theme", "Light", "Dark", "Auto"),
            selectRow("Accent color", "Highlight color for the interface", "Teal", "Blue", "Coral"),
            selectRow("Font size", "Adjust the overall font size", "14px"),
            toggleRow("Compact mode", "Use a more compact layout", false));
    }

    private VBox createAboutPage() {
        return settingsCard("About PulseOS", 
            new Label("PulseOS\\nIntelligent Device Utility & Hardware Health"),
            new Label("Version                                             v1.0.0"),
            new Label("Update status                                  ● Up to date"),
            new Label("License: MIT Open Source"));
    }

    private VBox settingsCard(String title, javafx.scene.Node... rows) {
        VBox card = new VBox(10);
        card.getStyleClass().add("settings-card");
        card.setPrefWidth(600);
        
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-card-title");
        
        card.getChildren().add(heading);
        card.getChildren().add(new Separator());
        card.getChildren().addAll(rows);
        
        return card;
    }

    private CheckBox toggleRow(String title, String detail, boolean selected) {
        CheckBox box = new CheckBox(title + "\\n" + detail);
        box.setSelected(selected);
        box.getStyleClass().add("settings-toggle-row");
        return box;
    }

    private ComboBox<String> selectRow(String title, String detail, String... options) {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(options); 
        box.setValue(options[0]);
        box.setPromptText(title + " · " + detail);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("settings-select-row");
        return box;
    }
}
