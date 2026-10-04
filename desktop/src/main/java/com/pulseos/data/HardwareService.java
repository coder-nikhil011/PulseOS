package com.pulseos.data;

import java.util.List;

public interface HardwareService {
    List<HardwareReading> getReadings();
    record HardwareReading(String name, String status, String detail) {}
}
