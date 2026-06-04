package com.zoka.zkapriv.network.nativebridge

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import com.google.gson.Gson
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

class LocalProverService : Service() {
    private val gson = Gson()
    private lateinit var workerThread: HandlerThread
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("zoka-local-prover")
        workerThread.start()
        messenger = Messenger(Handler(workerThread.looper, ::handleMessage))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        workerThread.quitSafely()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        val requestPipe = message.data.parcelFileDescriptor(REQUEST_PIPE)
        val responsePipe = message.data.parcelFileDescriptor(RESPONSE_PIPE)
        if (requestPipe == null || responsePipe == null) return true

        val response = runCatching {
            val request = readFramedUtf8(requestPipe)
            when (message.what) {
                LocalProverClient.COMMAND_BUILD -> ZokaNativeWallet.build(request)
                LocalProverClient.COMMAND_PRELOAD -> ZokaNativeWallet.preload(request)
                LocalProverClient.COMMAND_PING -> PONG
                LocalProverClient.COMMAND_BENCHMARK_V2_I1_LAB ->
                    ZokaNativeWallet.benchmarkV2I1Lab(request)
                else -> error("Unsupported local prover command")
            }
        }.getOrElse { error ->
            gson.toJson(mapOf("error" to (error.message ?: "Local prover service failed")))
        }

        runCatching {
            writeFramedUtf8(responsePipe, response)
        }
        requestPipe.close()
        responsePipe.close()
        return true
    }

    private fun readFramedUtf8(pipe: ParcelFileDescriptor): String =
        DataInputStream(FileInputStream(pipe.fileDescriptor)).use { input ->
            val length = input.readInt()
            require(length in 0..MAX_PIPE_PAYLOAD_BYTES) {
                "Local prover request exceeds pipe limit"
            }
            val bytes = input.readNBytes(length)
            require(bytes.size == length) { "Local prover request pipe ended early" }
            String(bytes, Charsets.UTF_8)
        }

    private fun writeFramedUtf8(pipe: ParcelFileDescriptor, response: String) {
        val bytes = response.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PIPE_PAYLOAD_BYTES) {
            "Local prover response exceeds pipe limit"
        }
        DataOutputStream(FileOutputStream(pipe.fileDescriptor)).use { output ->
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.parcelFileDescriptor(key: String): ParcelFileDescriptor? =
        getParcelable(key)

    companion object {
        internal const val REQUEST_PIPE = "request_pipe"
        internal const val RESPONSE_PIPE = "response_pipe"
        internal const val MAX_PIPE_PAYLOAD_BYTES = 16 * 1024 * 1024
        private const val PONG = "pong"
    }
}
