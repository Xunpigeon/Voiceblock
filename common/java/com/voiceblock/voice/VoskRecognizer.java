package com.voiceblock.voice;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.client.Minecraft;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.nio.file.*;

/**
 * Vosk 离线中文语音识别器。
 *
 * 模型加载优先级：
 * 1. config/voiceblock/vosk-model （用户外置模型，可放大模型 vosk-model-cn-0.22）
 * 2. jar 内 assets/voiceblock/vosk-model （内置小模型，开箱即用）
 *
 * 如需更高精度，下载 vosk-model-cn-0.22 (1.3GB) 解压到 config/voiceblock/vosk-model 即可覆盖。
 */
public class VoskRecognizer {

    private static VoskRecognizer instance;
    private Model model;
    private static volatile boolean initFailed = false;

    private VoskRecognizer() throws IOException {
        Path modelDir = getModelDir();
        if (isValidModel(modelDir)) {
            VoiceBlockMod.LOGGER.info("Using external Vosk model from {}", modelDir);
        } else {
            VoiceBlockMod.LOGGER.info("External Vosk model not found at {}, extracting bundled model from jar...", modelDir);
            try {
                extractModelFromJar(modelDir);
            } catch (IOException e) {
                initFailed = true;
                String msg = "Vosk 模型未找到！请下载中文模型并解压到 " + modelDir.toAbsolutePath() +
                        "\n推荐模型：vosk-model-cn-0.22 (高精度, 1.3GB) 或 vosk-model-small-cn-0.22 (轻量, 42MB)" +
                        "\n下载地址：https://alphacephei.com/vosk/models";
                VoiceBlockMod.LOGGER.error(msg);
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[VoiceBlock] 语音模型未找到！请查看日志获取下载说明。"), false);
                    }
                } catch (Exception ignored) {}
                throw new IOException(msg, e);
            }
        }
        model = new Model(modelDir.toString());
        VoiceBlockMod.LOGGER.info("Vosk model loaded from {}", modelDir);
    }

    /**
     * 检查目录是否为有效的 Vosk 模型目录。
     */
    private boolean isValidModel(Path dir) {
        return Files.isDirectory(dir)
                && Files.exists(dir.resolve("conf"))
                && Files.exists(dir.resolve("conf").resolve("model.conf"))
                && Files.isDirectory(dir.resolve("am"))
                && Files.exists(dir.resolve("am").resolve("final.mdl"));
    }

    public static synchronized VoskRecognizer getInstance() {
        if (instance == null && !initFailed) {
            try {
                instance = new VoskRecognizer();
            } catch (IOException e) {
                VoiceBlockMod.LOGGER.error("Failed to initialize VoskRecognizer", e);
            }
        }
        return instance;
    }

    public static boolean isReady() {
        return instance != null && instance.model != null;
    }

    private Path getModelDir() {
        return Paths.get("config", "voiceblock", "vosk-model");
    }

    /**
     * 从 jar 资源中解压模型到 config 目录。
     */
    private void extractModelFromJar(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        ClassLoader cl = getClass().getClassLoader();
        InputStream listStream = cl.getResourceAsStream("assets/voiceblock/vosk-model-files.txt");
        if (listStream == null) {
            throw new IOException("Vosk model manifest not found in jar.");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(listStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Path out = targetDir.resolve(line);
                Files.createDirectories(out.getParent());
                try (InputStream in = cl.getResourceAsStream("assets/voiceblock/vosk-model/" + line)) {
                    if (in == null) {
                        VoiceBlockMod.LOGGER.warn("Missing model file in jar: {}", line);
                        continue;
                    }
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        VoiceBlockMod.LOGGER.info("Bundled Vosk model extracted to {}", targetDir);
    }

    /**
     * 识别一段 PCM 音频（16kHz, 16bit, 单声道）。
     */
    public synchronized String recognize(byte[] pcmData) {
        if (model == null) return "";
        try (Recognizer recognizer = new Recognizer(model, 16000.0f)) {
            recognizer.setWords(false);
            recognizer.acceptWaveForm(pcmData, pcmData.length);
            String result = recognizer.getFinalResult();
            String text = extractText(result);
            return fixEncoding(text);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Vosk recognition error", e);
            return "";
        }
    }

    private String fixEncoding(String text) {
        if (text == null || text.isEmpty()) return text;
        boolean hasChinese = text.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        if (hasChinese) return text;
        try {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            boolean fixedHasChinese = fixed.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
            if (fixedHasChinese) {
                VoiceBlockMod.LOGGER.info("Fixed encoding: {} -> {}", text, fixed);
                return fixed;
            }
        } catch (Exception ignored) {}
        return text;
    }

    private String extractText(String json) {
        if (json == null) return "";
        int start = json.indexOf("\"text\"");
        if (start < 0) return json.trim();
        int colon = json.indexOf(':', start);
        int quote1 = json.indexOf('"', colon + 1);
        if (quote1 < 0) return "";
        int quote2 = json.indexOf('"', quote1 + 1);
        if (quote2 < 0) return "";
        return json.substring(quote1 + 1, quote2).trim();
    }
}