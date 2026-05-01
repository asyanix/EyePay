package com.asyachz.eyepayapp

import android.app.Application
import com.asyachz.eyepayapp.data.AppDatabase
import com.asyachz.eyepayapp.tts.TtsManager

class EyePayApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val cardRepository by lazy { database.cardDao() }
    val ttsManager by lazy { TtsManager(this) }

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
    }
}