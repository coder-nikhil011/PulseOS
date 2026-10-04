package com.pulseos.data;

import java.util.List;

public final class MockHardwareService implements HardwareService {
    @Override public List<HardwareReading> getReadings() {
        return List.of(
                new HardwareReading("CPU / Processor", "GOOD", "Load 14.5% · Clock 2.96 GHz"),
                new HardwareReading("RAM / Memory", "GOOD", "Used 79.6% · 8.87 / 8.00 GB"),
                new HardwareReading("GPU / Graphics", "GOOD", "Apple A18 Pro · health sensors limited"),
                new HardwareReading("Storage / SSD", "GOOD", "Capacity 29.1% used · SMART health unavailable"),
                new HardwareReading("Battery / Power", "GOOD", "Charge 98% · charging"),
                new HardwareReading("Fan / Cooling", "LIMITED", "Fan RPM is not exposed by this device"),
                new HardwareReading("Thermal Sensors", "GOOD", "CPU temperature 54.1°C"),
                new HardwareReading("Network", "PROBLEM", "DNS or internet reachability failed"));
    }
}
