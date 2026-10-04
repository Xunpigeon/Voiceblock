package com.voiceblock.voice;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

/**
 * 麦克风录音器，采集 16kHz / 16bit / 单声道 PCM 音频（Vosk 要求格式）。
 */
public class MicrophoneRecorder {

    private static final float SAMPLE_RATE = 16000f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean capturing = false;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized void start() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                CHANNELS * 2,          // frame size = 通道数 * 采样字节
                SAMPLE_RATE,
                false                   // little-endian
        );

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("16kHz/16bit/mono 麦克风不支持");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        capturing = true;
        buffer.reset();

        captureThread = new Thread(this::captureLoop, "VoiceBlock-Mic");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {
        byte[] data = new byte[line.getBufferSize() / 5];
        while (capturing && line != null) {
            int bytesRead = line.read(data, 0, data.length);
            if (bytesRead > 0) {
                synchronized (buffer) {
                    buffer.write(data, 0, bytesRead);
                }
            }
        }
    }

    public synchronized byte[] stop() {
        capturing = false;
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {}
            captureThread = null;
        }
        synchronized (buffer) {
            return buffer.toByteArray();
        }
    }
}
