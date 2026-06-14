package com.example.dogmessengerclient

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AESCrypto {
    // Llave simétrica de 128 bits (16 bytes exactos)
    private val key = "DogMessenger2026".toByteArray(Charsets.UTF_8)
    private val secretKeySpec = SecretKeySpec(key, "AES")

    fun encrypt(message: String): String {
        // Se usa AES en modo ECB. En producción se usaria CBC con un vector de inicialización,
        // para la práctica de sockets esto garantiza un bloque cifrado estable.
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))

        // Lo pasamos a Base64 para que viaje como texto seguro por el socket
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
        val decodedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}