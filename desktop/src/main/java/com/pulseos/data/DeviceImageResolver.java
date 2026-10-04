package com.pulseos.data;

public final class DeviceImageResolver {
    private DeviceImageResolver() {}

    public static String resolve(DeviceService.DeviceInfo device) {
        String model = device.model().toLowerCase();
        String manufacturer = device.manufacturer().toLowerCase();
        if (manufacturer.contains("apple") && model.contains("macbook pro")) return "/devices/macbook-pro.png";
        if (manufacturer.contains("apple") && model.contains("macbook air")) return "/devices/macbook-air.svg";
        if (manufacturer.contains("dell") && model.contains("xps")) return "/devices/dell-xps.svg";
        if (manufacturer.contains("lenovo") && model.contains("thinkpad")) return "/devices/thinkpad.svg";
        if (manufacturer.contains("hp")) return "/devices/hp-laptop.svg";
        return "/devices/generic-laptop.svg";
    }
}
