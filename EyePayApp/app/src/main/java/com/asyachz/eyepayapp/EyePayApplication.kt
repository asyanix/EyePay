package com.asyachz.eyepayapp

import android.app.Application
import com.asyachz.eyepayapp.data.AppDatabase

class EyePayApplication : Application() {

    // Ручная реализация Dependency Injection
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val cardRepository by lazy { database.cardDao() }

    override fun onCreate() {
        super.onCreate()
        // Принудительная инициализация SQLCipher библиотек
        System.loadLibrary("sqlcipher")
    }
}