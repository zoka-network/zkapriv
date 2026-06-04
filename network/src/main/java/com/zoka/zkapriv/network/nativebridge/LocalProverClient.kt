package com.zoka.zkapriv.network.nativebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class LocalProverClient @Inject constructor(
    @ApplicationContext context: Context
) {
    private val applicationContext = context.applicationContext
    private val connectionMutex = Mutex()

    @Volatile
    private var serviceMessenger: Messenger? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    suspend fun build(requestJson: String): String = transact(COMMAND_BUILD, requestJson)

    suspend fun preload(trustedSetupDirectory: String): String =
        transact(COMMAND_PRELOAD, trustedSetupDirectory)

    suspend fun ping(payload: String = ""): String = transact(COMMAND_PING, payload)

    suspend fun benchmarkV2I1Lab(trustedSetupDirectory: String): String =
        transact(COMMAND_BENCHMARK_V2_I1_LAB, trustedSetupDirectory)

    private suspend fun transact(command: Int, request: String): String =
        withTimeout(TRANSACTION_TIMEOUT_MS) {
            val requestPipe = ParcelFileDescriptor.createPipe()
            val responsePipe = ParcelFileDescriptor.createPipe()
            try {
                val message = Message.obtain(null, command).apply {
                    data = Bundle().apply {
                        putParcelable(LocalProverService.REQUEST_PIPE, requestPipe[0])
                        putParcelable(LocalProverService.RESPONSE_PIPE, responsePipe[1])
                    }
                }
                connectedMessenger().send(message)
                requestPipe[0].close()
                responsePipe[1].close()

                withContext(Dispatchers.IO) {
                    writeFramedUtf8(requestPipe[1], request)
                    readFramedUtf8(responsePipe[0])
                }.also { response ->
                    check(response.isNotBlank()) { "Local prover process returned an empty response" }
                }
            } catch (error: Exception) {
                clearConnection()
                throw error
            } finally {
                requestPipe.forEach { runCatching { it.close() } }
                responsePipe.forEach { runCatching { it.close() } }
            }
        }

    private fun writeFramedUtf8(pipe: ParcelFileDescriptor, request: String) {
        val bytes = request.toByteArray(Charsets.UTF_8)
        require(bytes.size <= LocalProverService.MAX_PIPE_PAYLOAD_BYTES) {
            "Local prover request exceeds pipe limit"
        }
        DataOutputStream(FileOutputStream(pipe.fileDescriptor)).use { output ->
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()
        }
    }

    private fun readFramedUtf8(pipe: ParcelFileDescriptor): String =
        DataInputStream(FileInputStream(pipe.fileDescriptor)).use { input ->
            val length = input.readInt()
            require(length in 0..LocalProverService.MAX_PIPE_PAYLOAD_BYTES) {
                "Local prover response exceeds pipe limit"
            }
            val bytes = input.readNBytes(length)
            require(bytes.size == length) { "Local prover response pipe ended early" }
            String(bytes, Charsets.UTF_8)
        }

    private suspend fun connectedMessenger(): Messenger {
        serviceMessenger?.let { return it }
        return connectionMutex.withLock {
            serviceMessenger ?: bindService()
        }
    }

    private suspend fun bindService(): Messenger = suspendCancellableCoroutine { continuation ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("Local prover service returned no binder")
                        )
                    }
                    return
                }
                val messenger = Messenger(service)
                serviceMessenger = messenger
                if (continuation.isActive) continuation.resume(messenger)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceMessenger = null
            }
        }
        serviceConnection = connection
        val bound = applicationContext.bindService(
            Intent(applicationContext, LocalProverService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound && continuation.isActive) {
            serviceConnection = null
            continuation.resumeWithException(
                IllegalStateException("Could not bind local prover service")
            )
        }
        continuation.invokeOnCancellation {
            if (serviceMessenger == null) clearConnection()
        }
    }

    private fun clearConnection() {
        val connection = serviceConnection ?: return
        serviceConnection = null
        serviceMessenger = null
        runCatching { applicationContext.unbindService(connection) }
    }

    internal companion object {
        const val COMMAND_BUILD = 1
        const val COMMAND_PRELOAD = 2
        const val COMMAND_PING = 3
        const val COMMAND_BENCHMARK_V2_I1_LAB = 4
        private const val TRANSACTION_TIMEOUT_MS = 6L * 60L * 1000L
    }
}
