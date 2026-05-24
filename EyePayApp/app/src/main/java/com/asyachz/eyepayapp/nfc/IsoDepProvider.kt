package com.asyachz.eyepayapp.nfc

import android.nfc.tech.IsoDep
import com.github.devnied.emvnfccard.parser.IProvider
import java.io.IOException

class IsoDepProvider(private val isoDep: IsoDep) : IProvider {
    override fun transceive(pCommand: ByteArray): ByteArray {
        return try {
            isoDep.transceive(pCommand)
        } catch (e: IOException) {
            throw IOException("Communication with the card is interrupted", e)
        }
    }

    override fun getAt(): ByteArray {
        return isoDep.historicalBytes ?: isoDep.hiLayerResponse ?: ByteArray(0)
    }
}