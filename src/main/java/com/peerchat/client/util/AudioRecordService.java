package com.peerchat.client.util;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

// Dịch vụ ghi âm giọng nói từ Microphone qua Java Sound API tiêu chuẩn
// Nén âm thanh chuẩn PCM 16kHz, 16-bit, Mono thành tập tin WAV dung lượng nhẹ (~32 KB/s)
public class AudioRecordService {
    private static final Logger LOGGER = Logger.getLogger(AudioRecordService.class.getName());

    private TargetDataLine targetLine;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);
    private Thread recordThread;
    private ByteArrayOutputStream recordBuffer;
    private AudioFormat audioFormat;

    public AudioRecordService() {
        // Chuẩn định dạng âm thanh thoại tối ưu (16kHz, 16bit, Mono)
        this.audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                16000.0f,
                16,
                1,
                2,
                16000.0f,
                false // Little Endian
        );
    }

    // Bắt đầu thu âm giọng nói
    public synchronized boolean startRecording() {
        if (isRecording.get()) {
            LOGGER.warning("[AUDIO_REC] Đang trong phiên ghi âm.");
            return false;
        }

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(info)) {
                LOGGER.severe("[AUDIO_REC_ERR] Microphone không hỗ trợ định dạng thoại 16kHz.");
                return false;
            }

            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(audioFormat);
            targetLine.start();

            isRecording.set(true);
            recordBuffer = new ByteArrayOutputStream();

            recordThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording.get()) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        recordBuffer.write(buffer, 0, bytesRead);
                    }
                }
            }, "AudioRecord-Thread");
            recordThread.setDaemon(true);
            recordThread.start();

            LOGGER.info("[AUDIO_REC_START] Đang thu âm qua Microphone...");
            return true;
        } catch (LineUnavailableException e) {
            LOGGER.log(Level.SEVERE, "[AUDIO_LINE_ERR] Không thể truy cập Microphone: " + e.getMessage(), e);
            return false;
        }
    }

    // Dừng ghi âm và đóng gói thành file WAV
    public synchronized File stopRecording() {
        if (!isRecording.compareAndSet(true, false)) {
            return null;
        }

        try {
            if (targetLine != null) {
                targetLine.stop();
                targetLine.close();
            }

            if (recordThread != null) {
                recordThread.join(1000);
            }

            byte[] audioData = recordBuffer.toByteArray();
            if (audioData.length == 0) {
                LOGGER.warning("[AUDIO_REC] Bản ghi âm trống.");
                return null;
            }

            // Ghi dữ liệu PCM thô thành file WAV hoàn chỉnh
            File tempWavFile = File.createTempFile("voice_note_", ".wav");
            tempWavFile.deleteOnExit();

            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, audioFormat, audioData.length / audioFormat.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempWavFile);
            }

            LOGGER.info("[AUDIO_REC_SAVED] Đã đóng gói Voice Note: " + tempWavFile.getAbsolutePath() + " (" + tempWavFile.length() + " B)");
            return tempWavFile;

        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "[AUDIO_REC_SAVE_ERR] Lỗi lưu file WAV: " + e.getMessage(), e);
            return null;
        }
    }

    public boolean isRecording() {
        return isRecording.get();
    }
}
