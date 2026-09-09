package com.pulseos.ui.components;

import javafx.geometry.Pos;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class TelemetryChartCard extends VBox {
    private final AreaChart<String, Number> areaChart;
    private final XYChart.Series<String, Number> dataSeries;
    private final Label titleLabel;
    private final Label liveBadge;
    private int maxDataPoints = 45;

    public TelemetryChartCard() {
        this("Live Telemetry");
    }

    public TelemetryChartCard(String title) {
        getStyleClass().add("chart-card");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("chart-card-header");

        titleLabel = new Label(title);
        titleLabel.getStyleClass().add("chart-card-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        liveBadge = new Label("LIVE");
        liveBadge.getStyleClass().add("live-badge");
        header.getChildren().addAll(titleLabel, spacer, liveBadge);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Time");
        NumberAxis yAxis = new NumberAxis(0, 100, 20);
        yAxis.setLabel("Value");
        yAxis.setAutoRanging(false);

        areaChart = new AreaChart<>(xAxis, yAxis);
        areaChart.setCreateSymbols(false);
        areaChart.setAnimated(false);
        areaChart.setLegendVisible(false);
        areaChart.setHorizontalGridLinesVisible(true);
        VBox.setVgrow(areaChart, Priority.ALWAYS);

        dataSeries = new XYChart.Series<>();
        dataSeries.setName("Live");
        areaChart.getData().add(dataSeries);
        getChildren().addAll(header, areaChart);
    }

    public void configure(String title, String seriesName, String yAxisLabel, double min, double max) {
        titleLabel.setText(title);
        dataSeries.setName(seriesName);
        NumberAxis yAxis = (NumberAxis) areaChart.getYAxis();
        yAxis.setLabel(yAxisLabel);
        yAxis.setLowerBound(min);
        yAxis.setUpperBound(max);
        yAxis.setAutoRanging(false);
    }

    public void addTelemetryPoint(String timestampOrTick, double value) {
        if (!Double.isFinite(value)) return;
        dataSeries.getData().add(new XYChart.Data<>(timestampOrTick, value));
        if (dataSeries.getData().size() > maxDataPoints) dataSeries.getData().remove(0);
    }

    /**
     * Keeps the JavaFX scene graph bounded even when a card is reused for a long
     * running session.  Also trims an already populated chart when the limit is
     * changed (the old implementation only enforced the limit on new samples).
     */
    public void setMaxDataPoints(int maxDataPoints) {
        this.maxDataPoints = Math.max(1, maxDataPoints);
        while (dataSeries.getData().size() > this.maxDataPoints) {
            dataSeries.getData().remove(0);
        }
    }

    public void clearData() {
        dataSeries.getData().clear();
    }

    public AreaChart<String, Number> getAreaChart() { return areaChart; }
}
