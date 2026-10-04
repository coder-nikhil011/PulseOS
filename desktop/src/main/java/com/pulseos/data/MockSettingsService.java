package com.pulseos.data;

public final class MockSettingsService implements SettingsService {
    @Override public SettingsInfo getSettings() { return new SettingsInfo(true, true, true, "Stable (Recommended)", "English (United States)", "14px (Default)"); }
}
