package com.pulseos.telemetry;

import oshi.SystemInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Performs one background inventory pass for components that are not sampled every second. */
public final class PlatformCapabilityService {
    public record Capabilities(
            List<String> graphics,
            List<String> network,
            List<String> displays,
            List<String> usb,
            List<String> audio,
            List<String> bluetooth,
            String board,
            String firmware) {
        public static Capabilities empty() {
            return new Capabilities(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "");
        }
    }

    public CompletableFuture<Capabilities> scanAsync() {
        return CompletableFuture.supplyAsync(this::scan);
    }

    private Capabilities scan() {
        try {
            var hardware = new SystemInfo().getHardware();
            List<String> graphics = hardware.getGraphicsCards().stream()
                    .map(card -> card.getName() + " · " + card.getVendor()).toList();
            List<String> network = hardware.getNetworkIFs(true).stream()
                    .filter(networkIf -> networkIf.getIfOperStatus() != oshi.hardware.NetworkIF.IfOperStatus.DOWN)
                    .map(networkIf -> networkIf.getDisplayName() + " · " + networkIf.getSpeed() / 1_000_000 + " Mbps").toList();
            List<String> displays = hardware.getDisplays().stream()
                    .map(display -> display.getOutputName().orElse("Display detected")).toList();
            List<String> usb = hardware.getUsbDevices(false).stream()
                    .map(device -> device.getName()).toList();
            List<String> audio = hardware.getSoundCards().stream()
                    .map(soundCard -> soundCard.getName()).toList();
            List<String> bluetooth = hardware.getBluetoothDevices().stream()
                    .map(device -> device.getName()).toList();
            var computer = hardware.getComputerSystem();
            var board = computer.getBaseboard();
            var firmware = computer.getFirmware();
            return new Capabilities(graphics, network, displays, usb, audio, bluetooth,
                    board.getManufacturer() + " " + board.getModel(),
                    firmware.getManufacturer() + " " + firmware.getVersion());
        } catch (Exception error) {
            return Capabilities.empty();
        }
    }
}