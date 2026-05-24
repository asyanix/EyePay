package com.asyachz.eyepayapp.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.github.devnied.emvnfccard.parser.EmvTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class NfcManager(
    private val activity: Activity,
    private val onCardRead: (pan: String, expiry: String) -> Unit,
    private val onError: (String) -> Unit
) : NfcAdapter.ReaderCallback {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun enableReaderMode() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter?.enableReaderMode(activity, this, flags, null)
    }

    fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return
        val isoDep = IsoDep.get(tag) ?: return

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
                        onCardRead(pan.replace(" ", ""), expiry)
                    } else {
                        onError("Couldn't recognize the card details")
                    }
                }
            } catch (e: IOException) {
                Log.e("NfcManager", "Reading is interrupted: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("The card was removed too early. Try again.")
                }
            } catch (e: Exception) {
                Log.e("NfcManager", "Parsing error EMV: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Card reading error")
                }
            } finally {
                try { isoDep.close() } catch (e: IOException) {}
            }
        }
    }
}