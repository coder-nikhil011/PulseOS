package com.pulseos.data;

import java.util.List;

public final class MockConverterService implements ConverterService {
    @Override public List<ConversionInfo> getRecentConversions() { return List.of(
            new ConversionInfo("mountain.jpg", "JPG → PNG", "2.4 MB", "Completed"),
            new ConversionInfo("product_video.mp4", "MP4 → WEBM", "48.7 MB", "Completed"),
            new ConversionInfo("document.pdf", "PDF → DOCX", "1.8 MB", "Completed")); }
}
