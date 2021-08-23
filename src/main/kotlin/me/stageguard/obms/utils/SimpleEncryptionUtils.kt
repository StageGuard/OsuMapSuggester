package me.stageguard.obms.utils

import org.apache.commons.codec.binary.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SimpleEncryptionUtils {
    fun aesEncrypt(input: String, password: String): String {
        val cipher = Cipher.getInstance("AES")
        val keySpec = SecretKeySpec(password.toByteArray(),"AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypt = cipher.doFinal(input.toByteArray())
        return Base64().encodeToString(encrypt)
    }
    fun aesDecrypt(input: String, password: String): String {
        val cipher = Cipher.getInstance("AES")
        val keySpec = SecretKeySpec(password.toByteArray(),"AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decrypt = cipher.doFinal(Base64().decode(input))
        return String(decrypt)
    }
}