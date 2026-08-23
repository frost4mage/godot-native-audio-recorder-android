# Godot Native Audio Recorder (Android)

A Godot 4.x Android plugin that records microphone audio using Android's
native `AudioRecord` API instead of Godot's built-in `AudioStreamMicrophone`.

## Why does this exist?

Godot's built-in microphone recording (`AudioStreamMicrophone` +
`AudioEffectRecord`) has a long-standing resampler/drift bug on some
Android devices: after recording continuously for a while, small gaps
or timing drift appear in the captured audio. Common workarounds (like
periodically resetting the recording bus) only trade one bug for
another (e.g. losing a fraction of a second on every reset).

This plugin sidesteps the problem entirely by capturing audio directly
through Android's `AudioRecord`, on its own native thread, completely
independent of Godot's audio engine. The result is written straight to
a standard 16-bit PCM `.wav` file.

## Requirements

- Godot 4.x with **Custom Build** enabled (Project → Install Android
  Build Template) and **Use Gradle Build** turned on in your Android
  export preset.
- `RECORD_AUDIO` permission enabled in your export preset.

## Installation

1. Clone this repository.
2. Open it in Android Studio and build the plugin: ./gradlew assembleDebug (or `assembleRelease` for a release build)
3. Copy the following into your Godot project's `res://addons/` folder:
  addons/GodotNativeAudioRecorder/
  bin/debug/GodotNativeAudioRecorder-debug.aar
  plugin.cfg
  export_plugin.gd
  (`plugin.cfg` and `export_plugin.gd` come from `export_scripts_template/`
  in this repo; the `.aar` comes from your Gradle build output.)

  Alternatively, download a pre-built `.aar` from the
  [Releases](../../releases) page instead of building it yourself.
4. In Godot: **Project → Project Settings → Plugins**, make sure
   `GodotNativeAudioRecorder` is enabled.
5. In **Project → Export → (your Android preset) → Plugins**, tick
   `GodotNativeAudioRecorder`.

## Usage (GDScript)

```gdscript
var plugin: Object

func _ready():
    if OS.get_name() == "Android" and Engine.has_singleton("GodotNativeAudioRecorder"):
        plugin = Engine.get_singleton("GodotNativeAudioRecorder")
        plugin.connect("recording_error", _on_recording_error)

func start():
    var path = ProjectSettings.globalize_path("user://recording.wav")
    var ok = plugin.startRecording(path, 44100, false) # sample rate, stereo
    if not ok:
        print("Failed to start recording")

func stop():
    plugin.stopRecording()
    # user://recording.wav now contains a finished, playable WAV file
    var stream = AudioStreamWAV.load_from_file("user://recording.wav")

func _on_recording_error(reason: String) -> void:
    print("Recording error: ", reason)
```

### API

| Method | Description |
|---|---|
| `startRecording(path: String, sampleRateHz: int, stereo: bool) -> bool` | Starts recording to `path` (must be an absolute filesystem path — use `ProjectSettings.globalize_path()` on a `user://` path). Returns `false` immediately on failure (see the `recording_error` signal for the reason). |
| `stopRecording() -> bool` | Stops recording and finalizes the WAV header. |
| `isRecording() -> bool` | Whether a recording is currently in progress. |

### Signals

| Signal | Description |
|---|---|
| `recording_error(reason: String)` | Emitted when something goes wrong. Possible reasons: `permission_denied`, `invalid_buffer_size`, `security_exception`, `init_failed`, `file_open_failed: ...`, `io_exception: ...`. |

## Limitations

- Android only — there is no editor/desktop fallback built into the
  plugin itself. If you want to test in the editor, keep a separate
  recording path (e.g. Godot's own `AudioStreamMicrophone`) for
  non-Android platforms.
- Output is always 16-bit PCM WAV.
- Requires a real device to test (the microphone won't work in an
  emulator without a configured virtual audio input).

## License

MIT — see [LICENSE](LICENSE).
