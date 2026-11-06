package com.peerchat.client.util;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

// Dịch vụ phát lại tập tin âm thanh (.wav) nhúng trực tiếp qua Java Sound API
public class AudioPlayerService {
    private static final Logger LOGGER = Logger.getLogger(AudioPlayerService.class.getName());

    private Clip currentClip;
    private File currentPlayingFile;
    private Runnable onFinishCallback;

    // Phát tập tin âm thanh WAV
    public synchronized boolean play(File wavFile, Runnable onFinish) {
        stop();

        if (wavFile == null || !wavFile.exists()) {
            LOGGER.warning("[AUDIO_PLAY_ERR] File âm thanh không tồn tại.");
            return false;
        }

        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile);
            currentClip = AudioSystem.getClip();
            currentClip.open(ais);
            this.currentPlayingFile = wavFile;
            this.onFinishCallback = onFinish;

            currentClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    ClientUtils.runOnFxThread(() -> {
                        if (onFinishCallback != null) {
                            onFinishCallback.run();
                        }
                    });
                }
            });

            currentClip.start();
            LOGGER.info("[AUDIO_PLAY_START] Đang phát: " + wavFile.getName());
            return true;

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            LOGGER.log(Level.SEVERE, "[AUDIO_PLAY_ERR] Lỗi phát âm thanh: " + e.getMessage(), e);
            if (onFinish != null) {
                onFinish.run();
            }
            return false;
        }
    }

    // Dừng phát âm thanh
    public synchronized void stop() {
        if (currentClip != null) {
            try {
                if (currentClip.isRunning()) {
                    currentClip.stop();
                }
                currentClip.close();
            } catch (Exception ignored) {}
            currentClip = null;
        }
        currentPlayingFile = null;
    }

    public synchronized boolean isPlaying(File file) {
        return currentClip != null && currentClip.isRunning() && file != null && file.equals(currentPlayingFile);
    }

    public synchronized boolean isPlaying() {
        return currentClip != null && currentClip.isRunning();
    }
}
