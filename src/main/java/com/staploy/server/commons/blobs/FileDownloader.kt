package com.staploy.server.commons.blobs

import com.staploy.server.commons.service.Helpers
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.registry.pkg.RepoHandler
import io.ktor.client.request.header

import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.runBlocking

class FileDownloader {
    companion object {
        @JvmStatic
        fun downloadFileFromRemote(repoHandler: RepoHandler, blobId: String): String {
            val fileManager = Helpers.getFileRouteManager()
            val cacheFile = fileManager.registerNewCache(blobId)

            runBlocking {
                repoHandler.client.prepareGet(repoHandler.getApiUrl()) {
                    header(ServiceConsts.BLOB_REQ_TYPE, ServiceConsts.BLOB_REQ_TYPE_DOWNLOAD)
                    header(ServiceConsts.BLOB_REQ_TYPE_DOWNLOAD, blobId)

                    if (!repoHandler.repositoryToken.isNullOrEmpty()) {
                        header(HttpHeaders.Authorization, "Bearer ${repoHandler.repositoryToken}")
                    }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        return@execute
                    }

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    channel.toInputStream().use { inputStream ->
                        fileManager.getBlobFile(cacheFile).outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
            return cacheFile
        }
    }
}
