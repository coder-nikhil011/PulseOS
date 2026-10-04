package com.pulseos.data;

import java.util.List;

public interface ConverterService {
    List<ConversionInfo> getRecentConversions();
    record ConversionInfo(String fileName, String conversion, String size, String state) {}
}
