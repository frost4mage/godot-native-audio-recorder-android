package org.godotengine.plugin.android.audiorecorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class GodotNativeAudioRecorder(godot: Godot) : GodotPlugin(godot) {

    override fun getPluginName(): String {
        return BuildConfig.GODOT_PLUGIN_NAME
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("recording_error", String::class.java)
        )
    }

    private var sampleRate = 44100
    private var channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormatEnc = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private val isRecordingFlag = AtomicBoolean(false)
    private var raf: RandomAccessFile? = null

    /**
     * ضبط رو شروع می‌کنه و مستقیم به یه فایل WAV می‌نویسه.
     *
     * @param absoluteFilePath مسیر کامل فایل مقصد (نه res:// یا user://‎ — باید
     *        از قبل globalize شده باشه، مثلاً با ProjectSettings.globalize_path)
     * @param sampleRateHz نرخ نمونه‌برداری، پیش‌فرض 44100
     * @param stereo اگه true باشه استریو ضبط می‌کنه، وگرنه مونو (پیش‌فرض)
     */
    @UsedByGodot
    fun startRecording(absoluteFilePath: String, sampleRateHz: Int, stereo: Boolean): Boolean {
        if (isRecordingFlag.get()) return false

        sampleRate = if (sampleRateHz > 0) sampleRateHz else 44100
        channelConfig = if (stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            emitSignal("recording_error", "permission_denied")
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormatEnc)
        if (minBufferSize <= 0) {
            emitSignal("recording_error", "invalid_buffer_size")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormatEnc,
                minBufferSize * 4
            )
        } catch (e: SecurityException) {
            emitSignal("recording_error", "security_exception")
            return false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            emitSignal("recording_error", "init_failed")
            audioRecord?.release(); audioRecord = null
            return false
        }

        try {
            raf = RandomAccessFile(absoluteFilePath, "rw")
            raf?.setLength(0)
            raf?.write(ByteArray(44)) // جای هدر رو رزرو می‌کنیم
        } catch (e: Exception) {
            emitSignal("recording_error", "file_open_failed: ${e.message}")
            audioRecord?.release(); audioRecord = null
            return false
        }

        isRecordingFlag.set(true)
        audioRecord?.startRecording()

        recordingThread = Thread { captureLoop(minBufferSize) }
        recordingThread?.start()
        return true
    }

    @UsedByGodot
    fun stopRecording(): Boolean {
        if (!isRecordingFlag.get()) return false
        isRecordingFlag.set(false)
        recordingThread?.join(2000)
        recordingThread = null
        audioRecord?.apply { stop(); release() }
        audioRecord = null
        return true
    }

    @UsedByGodot
    fun isRecording(): Boolean = isRecordingFlag.get()

    private fun captureLoop(bufferSize: Int) {
        val data = ByteArray(bufferSize)
        var totalBytes = 0L
        try {
            while (isRecordingFlag.get()) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: -1
                if (read > 0) {
                    raf?.write(data, 0, read)
                    totalBytes += read
                }
            }
            writeWavHeader(totalBytes)
        } catch (e: Exception) {
            emitSignal("recording_error", "io_exception: ${e.message}")
        } finally {
            try { raf?.close() } catch (_: Exception) {}
            raf = null
        }
    }

    private fun writeWavHeader(totalAudioLen: Long) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
        val bitsPerSample = 16
        val blockAlign = channels * (bitsPerSample / 8)
        val byteRate = sampleRate.toLong() * blockAlign
        val totalDataLen = totalAudioLen + 36
        val header = ByteArray(44)
        val put = { idx: Int, v: Long, bytes: Int ->
            for (i in 0 until bytes) header[idx + i] = ((v shr (8 * i)) and 0xff).toByte()
        }
        "RIFF".toByteArray().copyInto(header, 0)
        put(4, totalDataLen, 4)
        "WAVEfmt ".toByteArray().copyInto(header, 8)
        put(16, 16, 4)
        put(20, 1, 2)
        put(22, channels.toLong(), 2)
        put(24, sampleRate.toLong(), 4)
        put(28, byteRate, 4)
        put(32, blockAlign.toLong(), 2)
        put(34, bitsPerSample.toLong(), 2)
        "data".toByteArray().copyInto(header, 36)
        put(40, totalAudioLen, 4)

        raf?.seek(0)
        raf?.write(header)
    }
}