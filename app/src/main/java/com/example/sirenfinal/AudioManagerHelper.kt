package com.example.sirenfinal

import android.content.Context
import android.media.AudioManager
import android.util.Log

object AudioManagerHelper {


    fun muteMusic(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager



            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                0,

                0
            )
        } catch (e: Exception) {
            Log.e("AudioManagerHelper", "Nie udało się wyciszyć muzyki: ${e.localizedMessage}")
        }
    }


    fun unmuteMusic(context: Context, volumeToRestore: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volumeToRestore,
                0
            )
        } catch (e: Exception) {
            Log.e("AudioManagerHelper", "Nie udało się przywrócić głośności: ${e.localizedMessage}")
        }
    }
}
