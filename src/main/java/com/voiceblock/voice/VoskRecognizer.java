package com.voiceblock.voice;

import com.voiceblock.VoiceBlockMod;
import net.minecraft.client.Minecraft;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.nio.file.*;

/**
 * Vosk 离线中文语音识别器。
 * 模型目录位置：config/voiceblock/vosk-model
 * 如果模型不存在，会尝试从 jar 内 assets/voiceblock/vosk-model 解压。
 */
public class VoskRecognizer {

    private static VoskRecognizer instance;
    private Model model;

    private VoskRecognizer() throws IOException {
        Path modelDir = getModelDir();
        if (!Files.isDirectory(modelDir) || !Files.exists(modelDir.resolve("conf"))) {
            VoiceBlockMod.LOGGER.info("Vosk model not found at {}, extracting from jar...", modelDir);
            extractModelFromJar(modelDir);
        }
        model = new Model(modelDir.toString());
        VoiceBlockMod.LOGGER.info("Vosk model loaded from {}", modelDir);
    }

    public static synchronized VoskRecognizer getInstance() throws IOException {
        if (instance == null) {
            instance = new VoskRecognizer();
        }
        return instance;
    }

    private Path getModelDir() {
        // 放在 Minecraft 运行目录下的 config/voiceblock/vosk-model
        return Paths.get("config", "voiceblock", "vosk-model");
    }

    /**
     * 从 jar 资源中解压模型到 config 目录。
     * 模型资源路径：assets/voiceblock/vosk-model/
     */
    private void extractModelFromJar(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        ClassLoader cl = getClass().getClassLoader();
        // 列出 jar 内模型文件清单（由打包时生成的清单文件读取）
        Path manifest = targetDir.resolveSibling("vosk-model-files.txt");
        InputStream listStream = cl.getResourceAsStream("assets/voiceblock/vosk-model-files.txt");
        if (listStream == null) {
            throw new IOException("Vosk model manifest not found in jar. " +
                    "请将中文模型放入 src/main/resources/assets/voiceblock/vosk-model/");
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
        VoiceBlockMod.LOGGER.info("Vosk model extracted to {}", targetDir);
    }

    /**
     * 识别一段 PCM 音频（16kHz, 16bit, 单声道）。
     *
     * @param pcmData 原始 PCM 字节
     * @return 识别出的文本
     */
    public synchronized String recognize(byte[] pcmData) {
        if (model == null) return "";
        try (Recognizer recognizer = new Recognizer(model, 16000.0f)) {
            recognizer.setWords(false);
            recognizer.acceptWaveForm(pcmData, pcmData.length);
            String result = recognizer.getFinalResult();
            // Vosk 返回 JSON，形如 {"text" : "橡木楼梯"}
            String text = extractText(result);
            // 备用编码修复：如果 jna.encoding 设置太晚导致中文乱码，尝试修复
            return fixEncoding(text);
        } catch (Exception e) {
            VoiceBlockMod.LOGGER.error("Vosk recognition error", e);
            return "";
        }
    }

    /**
     * 检测并修复可能的编码错误。
     * Vosk native 返回 UTF-8 字节，若 JNA 用 GBK 解码会产生乱码。
     * 检测方法：如果字符串中没有中文字符但有乱码特征，尝试用 UTF-8 重新解码。
     */
    private String fixEncoding(String text) {
        if (text == null || text.isEmpty()) return text;
        // 如果包含中文字符，认为编码正常
        boolean hasChinese = text.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        if (hasChinese) return text;
        // 尝试修复：将字符串按 ISO-8859-1 转回字节，再用 UTF-8 解码
        try {
            byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            String fixed = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            // 检查修复后是否有中文
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
