package com.peerchat.shared.util;

import java.io.File;
import java.text.DecimalFormat;

// Tiện ích xử lý đường dẫn, tên file và định dạng dung lượng
public final class FileUtils {
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("#,##0.0");

    private FileUtils() {}

    // Định dạng số byte thành chuỗi dễ đọc (B, KB, MB, GB)
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024L) {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    // Định dạng tốc độ truyền (ví dụ: 4.5 MB/s)
    public static String formatTransferSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) {
            return SPEED_FORMAT.format(bytesPerSec) + " B/s";
        } else if (bytesPerSec < 1024 * 1024) {
            return SPEED_FORMAT.format(bytesPerSec / 1024.0) + " KB/s";
        } else {
            return SPEED_FORMAT.format(bytesPerSec / (1024.0 * 1024.0)) + " MB/s";
        }
    }

    // Tự động đổi tên file nếu file đã tồn tại để tránh ghi đè
    public static File getUniqueDestinationFile(File directory, String originalFileName) {
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File target = new File(directory, originalFileName);
        if (!target.exists()) {
            return target;
        }

        String baseName;
        String extension;
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex != -1) {
            baseName = originalFileName.substring(0, dotIndex);
            extension = originalFileName.substring(dotIndex);
        } else {
            baseName = originalFileName;
            extension = "";
        }

        int counter = 1;
        while (target.exists()) {
            String newName = baseName + "_(" + counter + ")" + extension;
            target = new File(directory, newName);
            counter++;
        }
        return target;
    }

    // Lấy phần mở rộng (đuôi) của file
    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
