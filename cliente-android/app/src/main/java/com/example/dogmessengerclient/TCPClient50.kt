package com.example.dogmessengerclient

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TCPClient50(
    private val serverIp: String,
    private val portIp_: Int,
    private val messageListener: OnMessageReceived?
) {
    companion object {
        const val SERVERPORT = 8189
        private const val TAG = "TCPClient50"
    }

    private var mRun = false
    private var out: PrintWriter? = null
    private var `in`: BufferedReader? = null
    private var socket: Socket? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun sendMessage(message: String) {
        // Enviar mensaje en un hilo separado
        executor.execute {
            try {
                if (out != null && !out!!.checkError()) {
                    out!!.println(message)
                    out!!.flush()
                    Log.d(TAG, "Mensaje enviado: $message")
                } else {
                    Log.e(TAG, "No se puede enviar: out es null o tiene error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar mensaje: ${e.message}")
            }
        }
    }

    fun stopClient() {
        mRun = false
        executor.shutdown()
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error al cerrar socket: ${e.message}")
        }
    }

    fun run() {
        Thread {
            mRun = true
            try {
                val serverAddr = InetAddress.getByName(serverIp)
                Log.d(TAG, "Conectando a $serverIp:$SERVERPORT...")
                //socket = Socket(serverAddr, portIp_)
                socket = Socket(serverAddr, portIp_)

                try {
                    out = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
                    Log.d(TAG, "OutputStream listo")
                    `in` = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                    while (mRun) {
                        val serverMsg = `in`?.readLine()
                        if (!serverMsg.isNullOrEmpty() && messageListener != null) {
                            // Usar Handler para actualizar UI
                            Handler(Looper.getMainLooper()).post {
                                messageListener.messageReceived(serverMsg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en comunicación: ${e.message}")
                } finally {
                    socket?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al conectar: ${e.message}")
            }
        }.start()
    }

    interface OnMessageReceived {
        fun messageReceived(message: String)
    }
}