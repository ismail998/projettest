package com.ismailbelgacem.testvoice

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class VoiceBridgeService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var socketOut: OutputStream? = null
    private var socketIn: InputStream? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var sendJob: Job? = null
    private var receiveJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        scope.launch { initConnection() }
        return START_STICKY
    }

    private suspend fun initConnection() = withContext(Dispatchers.IO) {
        try {
            // الكمبيوتر المحلي (غيره حسب IP)
            val socket = Socket("192.168.1.10", 5000)

            socketOut = socket.getOutputStream()
            socketIn = socket.getInputStream()

            Log.d("VoiceService", "Connected to PC")

            setupAudio()
            startSending()
            startReceiving()

        } catch (e: Exception) {
            Log.e("VoiceService", "Error: ${e.message}")
            stopSelf()
        }
    }

    private fun setupAudio() {
        val sampleRate = 16000
        val buf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // 🔥 التقاط صوت المكالمة (root)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buf
        )

        audioRecord?.startRecording()

        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buf,
            AudioTrack.MODE_STREAM
        )

        audioTrack?.play()
    }

    private fun startSending() {
        sendJob = scope.launch {
            val buffer = ByteArray(2048)

            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    socketOut?.write(buffer, 0, read)
                }
            }
        }
    }

    private fun startReceiving() {
        receiveJob = scope.launch {
            val buffer = ByteArray(2048)

            while (isActive) {
                val read = socketIn?.read(buffer) ?: -1
                if (read > 0) {
                    audioTrack?.write(buffer, 0, read)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        sendJob?.cancel()
        receiveJob?.cancel()

        audioRecord?.stop()
        audioRecord?.release()

        audioTrack?.stop()
        audioTrack?.release()

        socketIn?.close()
        socketOut?.close()

        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}