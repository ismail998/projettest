package com.ismailbelgacem.testvoice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

class MainCallController(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun start() {
        telephony.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {

            when (state) {

                // رنّ الهاتف
                TelephonyManager.CALL_STATE_RINGING -> {
                    // يمكنك إضافة منطق خاص بك هنا
                }

                // بدأت المكالمة
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    routeAudioToPC()
                    startVoiceService()
                }

                // انتهت المكالمة
                TelephonyManager.CALL_STATE_IDLE -> {
                    stopVoiceService()
                    restoreAudio()
                }
            }
        }
    }

    private fun routeAudioToPC() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
    }

    private fun restoreAudio() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }

    private fun startVoiceService() {
        val intent = Intent(context, VoiceBridgeService::class.java)
        context.startService(intent)
    }

    private fun stopVoiceService() {
        val intent = Intent(context, VoiceBridgeService::class.java)
        context.stopService(intent)
    }
}