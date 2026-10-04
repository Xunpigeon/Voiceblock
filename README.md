# VoiceBlock

> Get any block in Minecraft simply by **speaking its name**.

VoiceBlock uses offline Chinese speech recognition to match your voice to blocks and place them directly in your hand — no internet, no commands, no inventory searching.

## ✨ Features

- **Voice-activated block retrieval** — Hold the configurable hotkey, say a block name, and get it instantly
- **Full offline recognition** — Powered by [Vosk](https://alphacephei.com/vosk/), no internet connection required
- **Smart matching** — Exact match, hand-crafted aliases, and pinyin similarity work together to understand casual speech
- **Modded block support** — Automatically discovers blocks from other mods and adds them to the recognizer
- **800+ vanilla blocks with aliases** — Say "半砖" for slabs, "围栏" for fences, and more
- **Real-time HUD display** — See what you said and what was matched right on screen
- **Cross-platform native libraries** — Works on Windows, Linux, and macOS

## 🎮 How to Use

1. Install the mod jar into .minecraft/mods/
2. Launch the game (first run extracts the speech model automatically)
3. Bind the **VoiceBlock** key in **Controls → Miscellaneous** (default: mouse button 5)
4. In Creative mode, hold the key and speak a block name
5. Release the key — the matched block appears in your selected hotbar slot

## 📋 Requirements

- Minecraft **1.21**
- NeoForge **21.0.167** or newer
- Java **21**
- A working microphone

## 🔧 For Developers

### Building

`powershell
# Windows
.\gradlew.bat build

# Linux / macOS
./gradlew build
`

Output jar: uild/libs/voiceblock-1.0.0.jar

### Project Structure

`
src/main/java/com/voiceblock/
├── VoiceBlockMod.java          # Mod entry point
├── client/
│   ├── ClientEvents.java       # Key press + recognition flow
│   ├── KeyBindings.java        # Key binding registration
│   └── VoiceHudOverlay.java    # HUD overlay for recognition feedback
├── match/
│   ├── BlockData.java          # Block registry + auto-discovery from game
│   ├── BlockMatcher.java       # Two-layer matching (exact → pinyin)
│   ├── MatchResult.java        # Result data class
│   └── PinyinUtil.java         # Pinyin similarity calculation
├── util/
│   └── InventoryUtil.java      # Creative-mode item sync with server
└── voice/
    ├── MicrophoneRecorder.java # PCM audio capture
    └── VoskRecognizer.java     # Vosk offline speech recognition
`

### Adding Block Aliases

Edit src/main/resources/assets/voiceblock/blocks.json to add hand-crafted aliases for better matching:

`json
{"id": "minecraft:oak_fence", "name": "橡木栅栏", "aliases": ["围栏", "栏杆"]}
`

Modded blocks are auto-discovered at runtime, but you can also add aliases for them in this file.

## 📦 Dependencies

| Library | Purpose |
|---------|---------|
| [Vosk](https://alphacephei.com/vosk/) 0.3.45 | Offline speech recognition |
| [pinyin4j](https://github.com/belerweb/pinyin4j) 2.5.1 | Pinyin conversion for fuzzy matching |

Both are bundled via JarJar — no separate downloads needed.

## 📝 License

MIT License — feel free to use, modify, and distribute.