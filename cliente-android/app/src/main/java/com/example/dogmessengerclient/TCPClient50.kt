package com.example.dogmessengerclient

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
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
    private var mOut: DataOutputStream? = null
    private var mIn: DataInputStream? = null
    private var socket: Socket? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendMessage(message: String) {
        executor.execute {
            try {
                if (mOut != null) {
                    // Protocolo: Convertir texto a bytes
                    val data = message.toByteArray(Charsets.UTF_8)

                    mOut!!.writeByte(1) // 1 byte: Tipo (1 = Texto)
                    mOut!!.writeInt(data.size) // 4 bytes: Longitud
                    mOut!!.write(data) // N bytes: Payload
                    mOut!!.flush()

                    Log.d(TAG, "Mensaje enviado: $message")
                } else {
                    Log.e(TAG, "No se puede enviar: mOut es null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar mensaje: ${e.message}")
            }
        }
    }

    fun sendBinaryFile(tipo: Int, data: ByteArray) {
        executor.execute {
            try {
                if (mOut != null) {
                    mOut!!.writeByte(tipo) // Tipo 2 (Imagen) o 3 (Archivo)
                    mOut!!.writeInt(data.size) // Longitud
                    mOut!!.write(data) // Vomitamos todos los bytes crudos
                    mOut!!.flush()
                    Log.d(TAG, "Archivo enviado. Tamaño: ${data.size} bytes")
                } else {
                    Log.e(TAG, "No se puede enviar: mOut es null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar archivo: ${e.message}")
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
                Log.d(TAG, "Conectando a $serverIp:$portIp_...")
                socket = Socket(serverAddr, portIp_)

                try {
                    // Instanciar flujos binarios
                    mOut = DataOutputStream(socket!!.getOutputStream())
                    mIn = DataInputStream(socket!!.getInputStream())
                    Log.d(TAG, "Flujo listos")

                    while (mRun) {
                        // Protocolo Leer Cabecera
                        val tipo = mIn!!.readByte().toInt()
                        val longitud = mIn!!.readInt()

                        // Leer Payload
                        val payload = ByteArray(longitud)
                        mIn!!.readFully(payload)

                        if (tipo == 1 && messageListener != null) {
                            val serverMsg = String(payload, Charsets.UTF_8)

                            // Actualizar UI en el hilo principal
                            mainHandler.post {
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