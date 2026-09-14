package com.github.xckevin927.android.battery.widget.billing

import android.content.Context
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** Private no-backup storage prevents a signed entitlement from moving to another installation. */
internal class ReceiptStore(context: Context) {
    data class Receipt(val originalJson: String, val signature: String)

    private val directory = context.applicationContext.noBackupFilesDir
    private val receiptFile = File(directory, "battery_widget_pro.receipt")
    private val atomicFile = AtomicFile(receiptFile)

    @Synchronized fun read(): Receipt? = runCatching {
        DataInputStream(atomicFile.openRead()).use { input ->
            val json = readBytes(input, MAX_JSON_BYTES).toString(Charsets.UTF_8)
            val signature = readBytes(input, MAX_SIGNATURE_BYTES).toString(Charsets.UTF_8)
            if (input.read() != -1 || json.isBlank() || signature.isBlank()) null else Receipt(json, signature)
        }
    }.getOrNull()

    @Synchronized fun write(receipt: Receipt): Boolean = runCatching {
        directory.mkdirs()
        val stream = atomicFile.startWrite()
        try {
            val output = DataOutputStream(stream)
            writeBytes(output, receipt.originalJson.toByteArray(Charsets.UTF_8), MAX_JSON_BYTES)
            writeBytes(output, receipt.signature.toByteArray(Charsets.UTF_8), MAX_SIGNATURE_BYTES)
            output.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
        true
    }.getOrDefault(false)

    @Synchronized fun clear() {
        atomicFile.delete()
    }

    private fun readBytes(input: DataInputStream, maximum: Int): ByteArray {
        val size = input.readInt()
        require(size in 1..maximum)
        return ByteArray(size).also(input::readFully)
    }

    private fun writeBytes(output: DataOutputStream, bytes: ByteArray, maximum: Int) {
        require(bytes.size in 1..maximum)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    companion object {
        private const val MAX_JSON_BYTES = 64 * 1024
        private const val MAX_SIGNATURE_BYTES = 8 * 1024
    }
}
