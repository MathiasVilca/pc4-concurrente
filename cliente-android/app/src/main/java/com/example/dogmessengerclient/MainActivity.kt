package com.example.dogmessengerclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
/////////////
//import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
//import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.run

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
    private lateinit var etMensaje: EditText
    private lateinit var btnEnviar: Button
    private lateinit var btnConectar: Button
    private lateinit var btnDesconectar: Button
    private lateinit var tvMensajes: TextView

    private lateinit var etPorts: EditText

    private lateinit var etIp: EditText
    private lateinit var scrollView: ScrollView

    private var mTcpClient: TCPClient50? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isConnected = false




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        ////add
        initViews()
        setupListeners()
        verificarPermisos()

    }
    private fun initViews() {
        etMensaje = findViewById(R.id.etMensaje_)
        btnEnviar = findViewById(R.id.btnEnviar_)
        btnConectar = findViewById(R.id.btnConectar_)
        btnDesconectar = findViewById(R.id.btnDesconectar_)
        tvMensajes = findViewById(R.id.tvMensajes_)
        etPorts = findViewById(R.id.etPort_)
        etIp = findViewById(R.id.etIP_)

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
    }

    private fun conectarAlServidor() {
        // Cambia esta IP por la de tu servidor
        //val serverIp = "192.168.100.18" // O usa "10.0.2.2" para emulador conectado a localhost
        val serverIp = etIp.text.toString()
        val miport = etPorts.text.toString()

        //if (serverIp == "192.168.73.215") {
        // Para pruebas, muestra un diálogo o usa un EditText para la IP
        //    Toast.makeText(this, "Configura la IP del servidor", Toast.LENGTH_SHORT).show()
        //    return
        //}

        agregarMensaje("Conectando al servidor $serverIp:${TCPClient50.SERVERPORT}...")

        mTcpClient = TCPClient50(serverIp, miport.toInt(),object : TCPClient50.OnMessageReceived {
            override fun messageReceived(message: String) {
                mainHandler.post {
                    agregarMensaje("Servidor: $message")
                }
            }
        })

        // Iniciar la conexión (ya maneja su propio hilo)
        mTcpClient?.run()

        // Esperar un momento para dar tiempo a la conexión
        mainHandler.postDelayed({
            isConnected = true
            habilitarEnvio(true)
            btnConectar.isEnabled = false
            btnDesconectar.isEnabled = true
            agregarMensaje("✓ Conectado al servidor")
            Toast.makeText(this, "Conectado al servidor", Toast.LENGTH_SHORT).show()
        }, 1000)
    }

    private fun desconectarDelServidor() {
        agregarMensaje("Desconectando del servidor...")
        mTcpClient?.stopClient()
        mTcpClient = null
        isConnected = false
        habilitarEnvio(false)
        btnConectar.isEnabled = true
        btnDesconectar.isEnabled = false
        agregarMensaje("✓ Desconectado del servidor")
        Toast.makeText(this, "Desconectado del servidor", Toast.LENGTH_SHORT).show()
    }

    private fun enviarMensaje() {
        val mensaje = etMensaje.text.toString().trim()

        if (mensaje.isEmpty()) {
            Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isConnected || mTcpClient == null) {
            Toast.makeText(this, "No estás conectado al servidor", Toast.LENGTH_SHORT).show()
            return
        }

        // El sendMessage ya maneja su propio hilo
        mTcpClient?.sendMessage(mensaje)
        agregarMensaje("Yo: $mensaje")
        etMensaje.text.clear()
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
    }

    private fun verificarPermisos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.INTERNET
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.INTERNET),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        desconectarDelServidor()
    }
}