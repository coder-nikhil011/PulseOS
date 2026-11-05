package com.pulseos.data;

import java.util.Locale;

public final class DeviceImageResolver {
    private DeviceImageResolver() {}

    public static String resolve(DeviceService.DeviceInfo device) {
        String model = device.model() == null ? "" : device.model().toLowerCase(Locale.ROOT);
        String manufacturer = device.manufacturer() == null ? "" : device.manufacturer().toLowerCase(Locale.ROOT);
        String os = device.operatingSystem() == null ? "" : device.operatingSystem().toLowerCase(Locale.ROOT);

        // Apple Mac devices (MacBook Pro, MacBook Air, Mac14,x, Mac15,x, Mac17,x, etc.)
        if (manufacturer.contains("apple") || model.startsWith("mac") || os.contains("mac")) {
            if (model.contains("air") || model.contains("mac13") || model.contains("mac14,2")) {
                return "/devices/macbook-air.svg";
            }
            return "/devices/macbook-pro.png";
        }

        // Windows / PC manufacturers
        if (manufacturer.contains("dell") && (model.contains("xps") || model.contains("precision"))) {
            return "/devices/dell-xps.svg";
        }
        if (manufacturer.contains("lenovo") && (model.contains("thinkpad") || model.contains("yoga"))) {
            return "/devices/thinkpad.svg";
        }
        if (manufacturer.contains("hp") || manufacturer.contains("hewlett")) {
            return "/devices/hp-laptop.svg";
        }

        return "/devices/generic-laptop.svg";
    }
}

