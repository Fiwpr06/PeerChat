package com.peerchat.shared.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Tiện ích tính và kiểm tra mã băm SHA-256 để xác thực tính toàn vẹn của file
public final class ChecksumUtils {
    private static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 64 * 1024; // Bộ đệm đọc 64 KB

    private ChecksumUtils() {}

    // Tính mã SHA-256 cho file trên đĩa (đọc theo luồng, không tốn RAM)
    public static String calculateSHA256(File file) throws IOException {
        try (InputStream fis = new FileInputStream(file)) {
            return calculateSHA256(fis);
        }
    }

    // Tính mã SHA-256 từ luồng InputStream
    public static String calculateSHA256(InputStream is) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Thuật toán SHA-256 không khả dụng", e);
        }
    }

    // Tính mã SHA-256 từ mảng byte trong bộ nhớ
    public static String calculateSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Thuật toán SHA-256 không khả dụng", e);
        }
    }

    // So sánh hai mã băm SHA-256 (không phân biệt chữ hoa thường)
    public static boolean verifyChecksum(String expectedChecksum, String actualChecksum) {
        if (expectedChecksum == null || actualChecksum == null) {
            return false;
        }
        return expectedChecksum.trim().equalsIgnoreCase(actualChecksum.trim());
    }

    // Chuyển mảng byte sang chuỗi hex 64 ký tự
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString().toLowerCase();
    }
}
