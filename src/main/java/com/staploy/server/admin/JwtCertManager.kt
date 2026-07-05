package com.staploy.server.admin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.staploy.Admin
import com.staploy.Admin.RequestPacket
import com.staploy.Users
import com.staploy.server.admin.Task.AuthContext
import com.staploy.server.commons.service.Helpers
import com.staploy.server.commons.service.InitHelperModule
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.commons.utils.Base64
import io.ktor.server.application.*
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

class JwtCertManager : InitHelperModule {

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

    companion object {
        @JvmStatic
        fun checkValidAuth(applicationCall: ApplicationCall, requestPacket: RequestPacket): AuthContext {
            if (requestPacket.getTaskGroup() == Admin.TaskGroup.TASK_USER && requestPacket.hasUserTaskType() && requestPacket.getUserTaskType()
                    .getUserTaskTypes() == Users.TaskUserTypes.TYPE_USER_LOGIN
            ) {
                return AuthContext(true, null)
            }

            var token = applicationCall.request.headers[AdminConst.HEADER_KEY_TOKEN]
            if (!Service.getInstance().argument.allowNonUser && token.isNullOrEmpty()) {
                return AuthContext(false, null)
            }

            if (token != null && token.startsWith("Bearer ")) {
                token = token.replace("Bearer ", "").trim { it <= ' ' }
                val decodedJWT = JWT.decode(token)

                try {
                    JWT.require(Helpers.getJwtCertManager().getRsaKey())
                        .withAudience(Service.getInstance().serverUUID)
                        .withIssuer(Service.getInstance().argument.host)
                        .build().verify(decodedJWT)
                } catch (_: java.lang.Exception) {
                    return AuthContext(false, null)
                }

                val uuid = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_UUID).asString()
                val username = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_USERNAME).asString()
                val version = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_VERSION).asLong()
                val permission = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_PERMISSION).asInt()

                val userPersistent = UserPersistent.fromUserName(username)
                if (!userPersistent.hasUser() || userPersistent.uuid != uuid) {
                    return AuthContext(false, null)
                }

                val userMetadata = userPersistent.getMetadata()
                return AuthContext(
                    userMetadata != null && (userMetadata.version == version && userMetadata.permissions == permission),
                    userMetadata
                )
            }

            return AuthContext(Service.getInstance().argument.allowNonUser, null)
        }
    }

    override fun onServiceAttache() {
        val arguments = Service.getInstance().argument
        if (arguments.enforceJwtAuth) {
            rsaKey =
                Algorithm.RSA256(loadPublicKey(arguments.jwtAuthPublicKey), loadPrivateKey(arguments.jwtAuthPrivateKey))
        }
    }

    override fun onServiceDetache() {

    }
}