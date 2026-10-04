package com.pulseos.data;

public interface SettingsService {
    SettingsInfo getSettings();
    record SettingsInfo(boolean launchOnStartup, boolean systemTray, boolean automaticUpdates, String updateChannel, String language, String fontSize) {}
}
