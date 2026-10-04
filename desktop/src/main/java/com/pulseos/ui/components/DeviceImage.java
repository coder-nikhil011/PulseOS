package com.pulseos.ui.components;

import com.pulseos.data.DeviceImageResolver;
import com.pulseos.data.DeviceService;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

public final class DeviceImage extends VBox {
    public DeviceImage(DeviceService.DeviceInfo device) {
        setPrefWidth(118);
        getStyleClass().add("laptop-visual");
        Image image = new Image(DeviceImageResolver.resolve(device), 140, 84, true, true);
        ImageView laptop = new ImageView(image);
        laptop.setPreserveRatio(true);
        laptop.setFitWidth(140);
        laptop.setFitHeight(84);
        Label caption = new Label(device.model());
        caption.getStyleClass().add("card-text");
        getChildren().addAll(laptop, caption);
    }
}
