package com.asyachz.eyepayapp.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import android.os.Bundle
import com.github.devnied.emvnfccard.parser.EmvTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class NfcManager(private val activity: Activity) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity.applicationContext)
    private val isProcessing = AtomicBoolean(false)

    var onCardReadListener: ((pan: String, expiry: String) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    fun enableReaderMode() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        try {
            nfcAdapter?.enableReaderMode(activity, this, flags, extras)
            Log.d("NfcManager", "Global NFC Reader Mode activated")
        } catch (e: Exception) {
            Log.e("NfcManager", "Failed to enable reader mode: ${e.message}")
        }
    }

    fun disableReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.e("NfcManager", "Failed to disable reader mode: ${e.message}")
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        val isoDep = IsoDep.get(tag) ?: return

        if (!isProcessing.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                isoDep.connect()
                isoDep.timeout = 5000

                val provider = IsoDepProvider(isoDep)
                val config = EmvTemplate.Config()
                    .setContactLess(true)
                    .setReadAllAids(true)
                    .setReadTransactions(false)

                val parser = EmvTemplate.Builder()
                    .setProvider(provider)
                    .setConfig(config)
                    .build()

                val card = parser.readEmvCard()

                val pan = card?.cardNumber ?: ""
                val expiry = card?.expireDate?.let {
                    val month = android.text.format.DateFormat.format("MM", it)
                    val year = android.text.format.DateFormat.format("yy", it)
                    "$month/$year"
                } ?: ""

                withContext(Dispatchers.Main) {
                    if (pan.isNotEmpty()) {
                        onCardReadListener?.invoke(pan.replace(" ", ""), expiry)
                    } else {
                        onErrorListener?.invoke("Couldn't recognize the card details")
                    }
                }
            } catch (e: IOException) {
                Log.e("NfcManager", "Reading is interrupted: ${e.message}")
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke("The card was removed too early. Try again.")
                }
            } catch (e: Exception) {
                Log.e("NfcManager", "Parsing error EMV: ${e.message}")
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke("Card reading error")
                }
            } finally {
                try { isoDep.close() } catch (e: IOException) {}
                kotlinx.coroutines.delay(500)
                isProcessing.set(false)
            }
        }
    }
}