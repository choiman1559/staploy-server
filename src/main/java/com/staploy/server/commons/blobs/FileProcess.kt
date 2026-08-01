package com.staploy.server.commons.blobs

import com.staploy.server.commons.service.Helpers
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.commons.utils.IOUtils
import com.staploy.server.packet.PacketWrapper
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class FileProcess {
    companion object {

        private val cacheLocks = ConcurrentHashMap<String, Mutex>()
        private val binaryCache = Collections.synchronizedMap(
            object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
                val MAX_ENTRIES = Service.getInstance().argument.maxBlobCacheEntities
                override fun removeEldestEntry(eldest: Map.Entry<String, ByteArray>) =
                    size > MAX_ENTRIES
            }
        )

        suspend fun getCached(blobToken: String, file: File): ByteArray {
            return binaryCache[blobToken] ?: run {
                val mutex = cacheLocks.getOrPut(blobToken) { Mutex() }
                mutex.withLock {
                    binaryCache[blobToken] ?: run {
                        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                        binaryCache[blobToken] = bytes
                        bytes
                    }
                }
            }
        }

        @JvmStatic
        suspend fun onReceiveMultiPart(applicationCall: ApplicationCall) {
            withContext(Dispatchers.IO) {
                val fileRouteManager = Helpers.getFileRouteManager()
                val multipart = applicationCall.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
                var fileName = ""

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            fileName = fileRouteManager.registerNewUpload(part.originalFileName as String)
                            val file = fileRouteManager.getBlobFile(fileName)
                            IOUtils.createNewFile(file, true)

                            part.provider().toInputStream().use { inputStream ->
                                file.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                                }
                            }
                        }
                        else -> { }
                    }
                    part.dispose()
                }
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(fileName))
            }
        }

        @JvmStatic
        suspend fun onRequestDownload(applicationCall: ApplicationCall) {
            val argument = Service.getInstance().argument
            val fileRouteManager = Helpers.getFileRouteManager()

            val blobToken = applicationCall.request.headers[ServiceConsts.BLOB_REQ_TYPE_DOWNLOAD]
            if (blobToken.isNullOrEmpty()) {
                applicationCall.respond(HttpStatusCode.BadRequest)
                return
            }
            val file = fileRouteManager.getBlobFile(blobToken)

            if(file.exists()) {
                val actualName = fileRouteManager.getActualName(blobToken)
                if(actualName != null) {
                    applicationCall.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName, actualName
                        ).toString()
                    )
                }

                if (!argument.useBlobCache || file.length() > argument.maxBlobCacheSize) {
                    applicationCall.respondFile(file)
                } else {
                    getCached(blobToken, file).let { applicationCall.respondBytes(it) }
                }
            } else {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("File not found", HttpStatusCode.NotFound, ""))
            }
        }
    }
}