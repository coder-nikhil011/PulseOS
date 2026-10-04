package com.pulseos.ui.views;

import com.pulseos.telemetry.SystemMetrics;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Problem 5 fix, expanded: shows (a) how many total processes/services are running
 * right now, and (b) which of them are the real CPU/RAM cost centers — this doubles
 * as the "what's draining my battery in the background" view, since sustained CPU
 * usage is what actually burns battery/thermal budget, whether or not you're
 * "using" that app right now. (OSHI has no reliable cross-platform way to detect
 * true foreground-window status, so we're honest and rank by real resource cost
 * instead of pretending to know which window has focus.)
 */
public class SmartRouterView extends VBox {

    private final TableView<SystemMetrics.ProcessInfo> table = new TableView<>();
    private final ObservableList<SystemMetrics.ProcessInfo> rows = FXCollections.observableArrayList();
    private final Label summaryLabel = new Label("Services / processes: —");
    private final Label impactSummaryLabel = new Label("");

    private static final double HIGH_IMPACT_CPU = 20.0;
    private static final double MODERATE_IMPACT_CPU = 5.0;

    public SmartRouterView() {
        this.getStyleClass().add("card");
        this.setSpacing(12);
        setPadding(new Insets(4));
        buildUi();
    }

    private void buildUi() {
        VBox sidebar = new VBox(4);
        sidebar.getStyleClass().add("view-shell-sidebar");
        Label sideTitle = new Label("SMART ROUTER");
        sideTitle.getStyleClass().add("sidebar-title");
        sidebar.getChildren().add(sideTitle);
        addSidebarButton(sidebar, "⌂  Overview", false);
        addSidebarButton(sidebar, "◉  Hardware Health", false);
        addSidebarButton(sidebar, "⌁  Smart Router", true);
        addSidebarButton(sidebar, "▣  Storage Healer", false);
        addSidebarButton(sidebar, "⇄  Converter", false);
        addSidebarButton(sidebar, "⚙  Settings", false);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        VBox main = new VBox(12);
        main.getStyleClass().add("view-shell-content");
        Label title = new Label("Smart Router");
        title.getStyleClass().add("view-title");
        Label subtitle = new Label("Manage network services, processes and optimize your connection.");
        subtitle.getStyleClass().add("view-subtitle");

        HBox summaryRow = new HBox(20, summaryLabel, impactSummaryLabel);
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        summaryLabel.getStyleClass().add("stat-caption-bold");
        impactSummaryLabel.getStyleClass().add("hw-text");

        TableColumn<SystemMetrics.ProcessInfo, String> nameCol = new TableColumn<>("Process / Service");
        nameCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().name()));
        nameCol.setPrefWidth(230);

        TableColumn<SystemMetrics.ProcessInfo, String> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cd.getValue().pid())));
        pidCol.setPrefWidth(70);

        TableColumn<SystemMetrics.ProcessInfo, String> cpuCol = new TableColumn<>("CPU %");
        cpuCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f%%", cd.getValue().cpuPercent())));
        cpuCol.setPrefWidth(80);

        TableColumn<SystemMetrics.ProcessInfo, String> ramCol = new TableColumn<>("RAM (MB)");
        ramCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.format("%.1f", cd.getValue().getRamMb())));
        ramCol.setPrefWidth(90);

        TableColumn<SystemMetrics.ProcessInfo, String> impactCol = new TableColumn<>("Battery / Perf Impact");
        impactCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                impactLabel(cd.getValue().cpuPercent())));
        impactCol.setPrefWidth(180);

        table.getColumns().setAll(List.of(nameCol, pidCol, cpuCol, ramCol, impactCol));
        table.setItems(rows);
        table.setPlaceholder(new Label("Telemetry se data aane ka wait kar rahe hain..."));

        VBox.setVgrow(table, Priority.ALWAYS);
        main.getChildren().addAll(title, subtitle, summaryRow, table);
        VBox.setVgrow(main, Priority.ALWAYS);
        HBox shell = new HBox(0, sidebar, main);
        HBox.setHgrow(main, Priority.ALWAYS);
        this.getChildren().add(shell);
    }

    private void addSidebarButton(VBox sidebar, String text, boolean active) {
        Button button = new Button(text);
        button.getStyleClass().add("view-sidebar-button");
        if (active) button.getStyleClass().add("view-sidebar-active");
        sidebar.getChildren().add(button);
    }

    private String impactLabel(double cpuPercent) {
        if (cpuPercent >= HIGH_IMPACT_CPU) return "🔴 High — draining battery";
        if (cpuPercent >= MODERATE_IMPACT_CPU) return "🟡 Moderate";
        return "🟢 Low";
    }

    public void update(SystemMetrics metrics) {
        updateProcesses(metrics.getTopProcesses(), metrics.getTotalProcessCount());
    }

    public void updateProcesses(List<SystemMetrics.ProcessInfo> processes, int totalProcessCount) {
        rows.setAll(processes == null ? List.of() : processes);

        List<SystemMetrics.ProcessInfo> safe = processes == null ? List.of() : processes;
        long highImpact = safe.stream().filter(p -> p.cpuPercent() >= HIGH_IMPACT_CPU).count();
        summaryLabel.setText(String.format("⚙ %,d services/processes running right now", totalProcessCount));
        impactSummaryLabel.setText(highImpact > 0
                ? "🔴 " + highImpact + " process(es) silently eating significant CPU — likely hurting battery & thermals"
                : "🟢 No silent resource hogs detected right now");
    }
}
