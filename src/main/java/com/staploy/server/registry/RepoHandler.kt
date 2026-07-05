package com.staploy.server.registry

import com.google.protobuf.util.JsonFormat
import com.staploy.Admin
import com.staploy.Registry
import com.staploy.Registry.RegistryRequestPacket
import com.staploy.Registry.RegistryResponsePacket
import com.staploy.server.commons.service.Helpers
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.commons.utils.Base64
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import java.net.URI

import kotlinx.coroutines.runBlocking

class RepoHandler {

    private val client: HttpClient = HttpClient(CIO)
    private var repositoryUrl: String? = null

    var repositoryToken: String? = null
        private set
    var packageCache: RegistryResponsePacket? = null
        private set

    @Throws(Exception::class)
    fun queryFromDb(token: Boolean, packages: Boolean) {
        val redisCommands = Helpers.getPersistsHelper().redisCommands
        if (token) {
            repositoryToken = redisCommands.hget(RegistryConst.SCHEME_REPOSITORY_LIST, repositoryUrl)
        }

        if (packages) {
            val rawData = redisCommands.hget(RegistryConst.SCHEME_REGISTRY_APP_LIST, repositoryUrl)
            packageCache = if (rawData.isEmpty()) null else RegistryResponsePacket.parseFrom(Base64.decode(rawData))
        }
    }

    fun createNewRepository(): Boolean {
        if (!exists()) {
            val redisCommands = Helpers.getPersistsHelper().redisCommands
            redisCommands.hset(RegistryConst.SCHEME_REPOSITORY_LIST, repositoryUrl, "")
            return true
        }
        return false
    }

    fun exists(): Boolean {
        val redisCommands = Helpers.getPersistsHelper().redisCommands
        return redisCommands.hexists(RegistryConst.SCHEME_REPOSITORY_LIST, repositoryUrl)
    }

    fun removeRepository(): Boolean {
        if (exists()) {
            val redisCommands = Helpers.getPersistsHelper().redisCommands
            redisCommands.hdel(RegistryConst.SCHEME_REPOSITORY_LIST, repositoryUrl)
            redisCommands.hdel(RegistryConst.SCHEME_REGISTRY_APP_LIST, repositoryUrl)

            repositoryToken = ""
            packageCache = null
            return true
        }
        return false
    }

    fun setAuthToken(token: String?) {
        repositoryToken = token
        val redisCommands = Helpers.getPersistsHelper().redisCommands
        redisCommands.hset(RegistryConst.SCHEME_REPOSITORY_LIST, repositoryUrl, token)
    }

    @Throws(Exception::class)
    fun requestPackageCache() {
        val registryRequestPacket = RegistryRequestPacket.newBuilder()
            .setTaskType(Registry.TaskRegistryTypes.TASK_LIST)
            .build()
        this.packageCache = postRequest(registryRequestPacket)

        val redisCommands = Helpers.getPersistsHelper().redisCommands
        redisCommands.hset(RegistryConst.SCHEME_REPOSITORY_CACHE, repositoryUrl, Base64.encode(registryRequestPacket.toByteArray()))
    }

    @Throws(Exception::class)
    private fun postRequest(registryRequestPacket: RegistryRequestPacket?): RegistryResponsePacket? {
        queryFromDb(true, packages = false)
        val responsePacket = runBlocking {
            val response: HttpResponse = client.get(repositoryUrl.toString()) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $repositoryToken")
                }
                setBody(JsonFormat.printer().print(registryRequestPacket))
            }

            val stringBody: String = response.body()
            val responsePacket = Admin.ResponsePacket.newBuilder()

            JsonFormat.parser().merge(stringBody, responsePacket)
            responsePacket.build()
        }

        if (responsePacket.status == ServiceConsts.STATUS_OK) {
            return responsePacket.registryResponse
        } else {
            throw IllegalStateException(responsePacket.errorCause)
        }
    }

    companion object {
        @JvmStatic
        @Throws(Exception::class)
        fun fromUrl(url: String): RepoHandler {
            val uri = URI(url.trim { it <= ' ' })
            val portStr = if (uri.port != -1) ":" + uri.port else ""
            val cleanUrl = uri.scheme + "://" + uri.host + portStr

            val repoHandler = RepoHandler()
            repoHandler.repositoryUrl = cleanUrl
            return repoHandler
        }

        @JvmStatic
        val allRepositories: MutableList<String?>?
            get() {
                val redisCommands =
                    Helpers.getPersistsHelper().redisCommands
                return redisCommands.hkeys(RegistryConst.SCHEME_REPOSITORY_LIST)
            }
    }
}
