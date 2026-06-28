package com.staploy.server.admin

import com.auth0.jwt.algorithms.Algorithm
import com.staploy.server.commons.service.InitHelperModule
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.utils.Base64
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

class JwtCertManager: InitHelperModule {

    private lateinit var rsaKey: Algorithm

    fun getRsaKey(): Algorithm {
        return rsaKey
    }

    private fun loadPrivateKey(filePath: String): RSAPrivateKey {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Crypto Error: Private Key file not found at $filePath")
        }

        val rawContent = file.readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")

        val sanitizedContent = rawContent.replace("[^A-Za-z0-9+/=]".toRegex(), "").trim()
        return try {
            val bytes = Base64.decode(sanitizedContent)
            val pkcs8Spec = PKCS8EncodedKeySpec(bytes)
            val keyFactory = KeyFactory.getInstance("RSA")

            keyFactory.generatePrivate(pkcs8Spec) as RSAPrivateKey
        } catch (e: Exception) {
            throw IllegalStateException(
                "Staploy Crypto Core Error: Failed to parse RSA Private Key from PEM ($filePath). " +
                        "Ensure the file is a valid PKCS#8 formatted private key.", e
            )
        }
    }

    private fun loadPublicKey(filePath: String): RSAPublicKey {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Crypto Error: Certificate file not found at $filePath")
        }

        return try {
            val certBytes = file.readBytes()
            val certFactory = CertificateFactory.getInstance("X.509")

            val certificate = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            val publicKey = certificate.publicKey

            if (publicKey !is RSAPublicKey) {
                throw IllegalArgumentException("The certificate does not contain a valid RSA Public Key.")
            }

            publicKey
        } catch (e: Exception) {
            throw IllegalStateException(
                "Staploy Crypto Core Error: Failed to extract RSA Public Key from X.509 Certificate ($filePath). " +
                        "Ensure the file is a valid server-cert.pem.", e
            )
        }
    }

    override fun onServiceAttache() {
        val arguments = Service.getInstance().argument
        if(arguments.enforceJwtAuth) {
            rsaKey = Algorithm.RSA256(loadPublicKey(arguments.jwtAuthPublicKey), loadPrivateKey(arguments.jwtAuthPrivateKey))
        }
    }

    override fun onServiceDetache() {

    }
}