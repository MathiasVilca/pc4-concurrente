package com.example.dogmessengerclient

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var etMensaje: EditText
    private lateinit var etPorts: EditText
    private lateinit var etIp: EditText
    private lateinit var etIdChat: EditText
    private lateinit var etCloneToken: EditText
    private lateinit var btnEnviar: Button
    private lateinit var btnConectar: Button
    private lateinit var btnDesconectar: Button
    private lateinit var btnEnviarImagen: Button
    private lateinit var btnEnviarArchivo: Button
    private lateinit var btnComprar: Button
    private lateinit var btnReporte: Button
    private lateinit var btnEmitirQr: Button
    private lateinit var btnEscanearQr: Button
    private lateinit var btnClonar: Button
    private lateinit var tvMensajes: TextView
    private lateinit var tvQrToken: TextView
    private lateinit var scrollView: ScrollView

    private var mTcpClient: TCPClient50? = null
    private var mVentasClient: TCPClient50? = null
    private var mImagenClient: TCPClient50? = null
    private var mArchivosClient: TCPClient50? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingOwnAttachmentMessages = mutableSetOf<String>()
    private var assignedChatClientId: String? = null
    private var isConnected = false

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            enviarImagenAlServidor(it)
        }
    }

    private val selectFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            enviarArchivoAlServidor(it)
        }
    }

    private val scanQrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val token = result.data?.getStringExtra("SCAN_RESULT")
                ?: result.data?.dataString
                ?: ""
            if (token.isNotBlank()) {
                etCloneToken.setText(token)
                agregarMensaje("QR leido: $token")
                clonarHistorial()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupListeners()
        verificarPermisos()
        habilitarEnvio(false)
        actualizarTokenQr()
    }

    private fun initViews() {
        etMensaje = findViewById(R.id.etMensaje_)
        etPorts = findViewById(R.id.etPort_)
        etIp = findViewById(R.id.etIP_)
        etIdChat = findViewById(R.id.etIdChat_)
        etCloneToken = findViewById(R.id.etCloneToken_)
        btnEnviar = findViewById(R.id.btnEnviar_)
        btnConectar = findViewById(R.id.btnConectar_)
        btnDesconectar = findViewById(R.id.btnDesconectar_)
        btnComprar = findViewById(R.id.btnComprar_)
        btnReporte = findViewById(R.id.btnReporte_)
        btnEnviarImagen = findViewById(R.id.btnEnviarImagen_)
        btnEnviarArchivo = findViewById(R.id.btnEnviarArchivo_)
        btnEmitirQr = findViewById(R.id.btnEmitirQr_)
        btnEscanearQr = findViewById(R.id.btnEscanearQr_)
        btnClonar = findViewById(R.id.btnClonar_)
        tvMensajes = findViewById(R.id.tvMensajes_)
        tvQrToken = findViewById(R.id.tvQrToken_)
        scrollView = findViewById(R.id.scrollView_)
    }

    private fun setupListeners() {
        btnConectar.setOnClickListener {
            conectarAlServidor()
        }

        btnDesconectar.setOnClickListener {
            desconectarDelServidor()
        }

        btnEnviar.setOnClickListener {
            enviarMensaje()
        }

        btnEnviarImagen.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        btnEnviarArchivo.setOnClickListener {
            selectFileLauncher.launch("*/*")
        }

        btnEmitirQr.setOnClickListener {
            emitirTokenQr()
        }

        btnEscanearQr.setOnClickListener {
            escanearQr()
        }

        btnClonar.setOnClickListener {
            clonarHistorial()
        }

        btnComprar.setOnClickListener {
            if (mVentasClient != null) {
                val cliente = obtenerIdChat()
                mVentasClient?.sendMessage("COMPRAR:$cliente:PREMIUM")
                agregarMensaje("Yo: Procesando compra PREMIUM...")
            } else {
                Toast.makeText(this, "Nodo de ventas inactivo", Toast.LENGTH_SHORT).show()
            }
        }

        btnReporte.setOnClickListener {
            if (mVentasClient != null) {
                mVentasClient?.sendMessage("REPORTE")
                agregarMensaje("Yo: Solicitando metricas...")
            }
        }
    }

    private fun conectarAlServidor() {
        val serverIp = etIp.text.toString().trim()
        val miport = etPorts.text.toString().trim().toIntOrNull()

        if (serverIp.isEmpty() || miport == null) {
            Toast.makeText(this, "Configura IP y puerto validos", Toast.LENGTH_SHORT).show()
            return
        }

        agregarMensaje("Conectando al servidor $serverIp:$miport...")

        mTcpClient = TCPClient50(serverIp, miport, object : TCPClient50.OnMessageReceived {
            override fun messageReceived(message: String) {
                mainHandler.post {
                    val formattedMessage = formatearMensajeEntrante(message)
                    if (formattedMessage.isNotBlank()) {
                        agregarMensaje(formattedMessage)
                    }
                }
            }
        })
        mTcpClient?.run()

        mImagenClient = TCPClient50(serverIp, 8191, object : TCPClient50.OnMessageReceived {
            override fun messageReceived(message: String) {
                mainHandler.post {
                    agregarMensaje("Nodo Imagenes: $message")
                }
            }
        })
        mImagenClient?.run()

        mArchivosClient = TCPClient50(serverIp, 8190, object : TCPClient50.OnMessageReceived {
            override fun messageReceived(message: String) {
                mainHandler.post {
                    agregarMensaje("Nodo Archivos: $message")
                }
            }
        })
        mArchivosClient?.run()

        mVentasClient = TCPClient50(serverIp, 8192, object : TCPClient50.OnMessageReceived {
            override fun messageReceived(message: String) {
                mainHandler.post {
                    agregarMensaje("Ventas Bot:\n$message")
                }

                if (message.contains("COMPROBANTE")) {
                    mTcpClient?.sendMessage("SISTEMA: Pedido generado por ${obtenerIdChat()}:\n$message")
                }
            }
        })
        mVentasClient?.run()

        mainHandler.postDelayed({
            val idChat = obtenerIdChat()
            mTcpClient?.sendMessage("IDENT:$idChat")
            isConnected = true
            habilitarEnvio(true)
            btnConectar.isEnabled = false
            btnDesconectar.isEnabled = true
            agregarMensaje("Conectado como idClientChat $idChat")
            Toast.makeText(this, "Conectado al servidor", Toast.LENGTH_SHORT).show()
        }, 800)
    }

    private fun desconectarDelServidor() {
        if (mTcpClient == null && mVentasClient == null && mImagenClient == null && mArchivosClient == null) {
            return
        }

        agregarMensaje("Desconectando del servidor...")
        mTcpClient?.stopClient()
        mVentasClient?.stopClient()
        mImagenClient?.stopClient()
        mArchivosClient?.stopClient()
        mTcpClient = null
        mVentasClient = null
        mImagenClient = null
        mArchivosClient = null
        isConnected = false
        habilitarEnvio(false)
        btnConectar.isEnabled = true
        btnDesconectar.isEnabled = false
        agregarMensaje("Desconectado del servidor")
    }

    private fun enviarMensaje() {
        val mensaje = etMensaje.text.toString().trim()

        if (mensaje.isEmpty()) {
            Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConnected || mTcpClient == null) {
            Toast.makeText(this, "No estas conectado al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        if (mensaje.startsWith("/join ")) {
            val nuevoGrupo = mensaje.removePrefix("/join ").trim()
            mTcpClient?.sendMessage("JOIN:$nuevoGrupo")
            agregarMensaje("--- Te has movido al grupo: $nuevoGrupo ---")
            etMensaje.text.clear()
            return
        }

        val mensajeCifrado = AESCrypto.encrypt(mensaje)
        mTcpClient?.sendMessage(mensajeCifrado)
        agregarMensaje("Yo: $mensaje [cifrado]")
        etMensaje.text.clear()
    }

    private fun emitirTokenQr() {
        actualizarTokenQr()
        val token = tvQrToken.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("DogMessenger QR", token))
        etCloneToken.setText(token)
        agregarMensaje("QR emitido/copied: $token")
    }

    private fun escanearQr() {
        val intent = Intent("com.google.zxing.client.android.SCAN")
        intent.putExtra("SCAN_MODE", "QR_CODE_MODE")

        try {
            scanQrLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Instala un lector QR o pega el token manualmente", Toast.LENGTH_LONG).show()
        }
    }

    private fun clonarHistorial() {
        if (!isConnected || mTcpClient == null) {
            Toast.makeText(this, "Conectate antes de clonar", Toast.LENGTH_SHORT).show()
            return
        }

        val idClonado = extraerIdDesdeToken(etCloneToken.text.toString())
        if (idClonado.isEmpty()) {
            Toast.makeText(this, "Token QR vacio", Toast.LENGTH_SHORT).show()
            return
        }

        etIdChat.setText(idClonado)
        actualizarTokenQr()
        mTcpClient?.sendMessage("CLONE:$idClonado")
        agregarMensaje("Solicitando clonacion de idClientChat $idClonado...")
    }

    private fun actualizarTokenQr() {
        tvQrToken.text = "DOGQR://CLONE/${obtenerIdChat()}"
    }

    private fun obtenerIdChat(): String {
        val id = etIdChat.text.toString().trim()
        return id.ifEmpty { "0812" }
    }

    private fun extraerIdDesdeToken(token: String): String {
        val clean = token.trim()
        return when {
            clean.startsWith("DOGQR://CLONE/") -> clean.removePrefix("DOGQR://CLONE/").trim()
            clean.startsWith("CLONE:") -> clean.removePrefix("CLONE:").trim()
            else -> clean
        }
    }

    private fun formatearMensajeEntrante(message: String): String {
        if (message.startsWith("SISTEMA: ClienteAsignado:")) {
            assignedChatClientId = message.removePrefix("SISTEMA: ClienteAsignado:").trim()
            return message
        }

        if (message.startsWith("HISTORY_BEGIN:")) {
            return "--- Inicio historial ${message.substringAfter("HISTORY_BEGIN:")} ---"
        }

        if (message.startsWith("HISTORY_END:")) {
            return "--- Fin historial ${message.substringAfter("HISTORY_END:")} ---"
        }

        if (message.startsWith("HISTORY_ITEM:")) {
            return "[Historial] ${formatearPayloadChat(message.removePrefix("HISTORY_ITEM:"))}"
        }

        return formatearPayloadChat(message)
    }

    private fun formatearPayloadChat(message: String): String {
        if (!message.startsWith("Cliente ")) {
            return message
        }

        val partes = message.split(": ", limit = 2)
        if (partes.size != 2) {
            return message
        }

        val remitente = partes[0]
        val payload = partes[1]

        if (pendingOwnAttachmentMessages.remove(payload)) {
            return ""
        }

        return try {
            "$remitente: ${AESCrypto.decrypt(payload)} [descifrado]"
        } catch (e: Exception) {
            message
        }
    }

    private fun agregarMensaje(mensaje: String) {
        tvMensajes.append("$mensaje\n")
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun habilitarEnvio(habilitado: Boolean) {
        btnEnviar.isEnabled = habilitado
        etMensaje.isEnabled = habilitado
        btnEnviarImagen.isEnabled = habilitado
        btnEnviarArchivo.isEnabled = habilitado
        btnClonar.isEnabled = habilitado
        btnComprar.isEnabled = habilitado
        btnReporte.isEnabled = habilitado
        btnDesconectar.isEnabled = habilitado
    }

    private fun verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.INTERNET), PERMISSION_REQUEST_CODE)
            }
        }
    }

    private fun enviarImagenAlServidor(uri: Uri) {
        if (!isConnected) {
            Toast.makeText(this, "No estas conectado al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val fileInfo = obtenerInfoArchivo(uri, bytes.size)
                enviarBinarioDirecto(8191, 2, bytes, "Imagen", fileInfo.first)
            } else {
                Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo imagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enviarArchivoAlServidor(uri: Uri) {
        if (!isConnected) {
            Toast.makeText(this, "No estas conectado al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val fileInfo = obtenerInfoArchivo(uri, bytes.size)
                enviarBinarioDirecto(8190, 3, bytes, "Archivo", fileInfo.first)
            } else {
                Toast.makeText(this, "No se pudo leer el archivo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun obtenerInfoArchivo(uri: Uri, fallbackSize: Int): Pair<String, Long> {
        var nombre = uri.lastPathSegment ?: "archivo_recibido"
        var size = fallbackSize.toLong()
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (nameIndex >= 0) {
                    nombre = cursor.getString(nameIndex) ?: nombre
                }

                if (sizeIndex >= 0) {
                    val reportedSize = cursor.getLong(sizeIndex)
                    if (reportedSize > 0) {
                        size = reportedSize
                    }
                }
            }
        } catch (_: Exception) {
            size = fallbackSize.toLong()
        } finally {
            cursor?.close()
        }

        return Pair(nombre, size)
    }

    private fun enviarBinarioDirecto(port: Int, tipo: Int, bytes: ByteArray, etiqueta: String, nombreArchivo: String) {
        Thread {
            try {
                val mensajeChat = enviarAvisoRecibido(etiqueta, bytes.size, nombreArchivo, port, tipo, bytes)
                mainHandler.post {
                    agregarMensaje("Yo: $mensajeChat")
                    mTcpClient?.sendMessage(mensajeChat)
                    Toast.makeText(this, "$etiqueta enviado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    agregarMensaje("Error enviando $etiqueta al nodo $port: ${e.message}")
                    Toast.makeText(this, "Error enviando $etiqueta: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun enviarAvisoRecibido(
        etiqueta: String,
        size: Int,
        nombreArchivo: String,
        port: Int,
        tipo: Int,
        bytes: ByteArray
    ): String {
        val serverIp = etIp.text.toString().trim()
        val serverAddr = InetAddress.getByName(serverIp)
        val payload = empaquetarBinario(nombreArchivo, bytes)

        return Socket(serverAddr, port).use { socket ->
            socket.soTimeout = 5000
            val out = DataOutputStream(socket.getOutputStream())
            out.writeByte(tipo)
            out.writeInt(payload.size)
            out.write(payload)
            out.flush()

            val input = DataInputStream(socket.getInputStream())
            val responseType = input.readByte().toInt()
            val responseLength = input.readInt()
            val responsePayload = ByteArray(responseLength)
            input.readFully(responsePayload)
            val response = String(responsePayload, Charsets.UTF_8)

            if (responseType == 1 && response.startsWith("BINARY_OK:")) {
                response.removePrefix("BINARY_OK:")
            } else {
                "[$etiqueta recibido: recibido_cliente_local_${System.currentTimeMillis()} (${size} bytes)]"
            }
        }
    }

    private fun empaquetarBinario(nombreArchivo: String, bytes: ByteArray): ByteArray {
        val nombreConCliente = "CLIENTE_CHAT=${assignedChatClientId ?: "sin_id"}|$nombreArchivo"
        val nombreBytes = nombreConCliente.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.write("DOGMETA1".toByteArray(Charsets.UTF_8))
            out.writeInt(nombreBytes.size)
            out.write(nombreBytes)
            out.write(bytes)
        }
        return baos.toByteArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarDelServidor()
    }
}
