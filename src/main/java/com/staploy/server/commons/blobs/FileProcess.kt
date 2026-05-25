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
import io.ktor.server.response.respondFile
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyTo

class FileProcess {
    companion object {
        @JvmStatic
        suspend fun onReceiveMultiPart(applicationCall: ApplicationCall) {

            val fileRouteManager = Helpers.getFileRouteManager()
            val multipart = applicationCall.receiveMultipart()
            var fileName = ""

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = fileRouteManager.registerNewUpload(part.originalFileName as String)
                        val file = fileRouteManager.getBlobFile(fileName)
                        IOUtils.createNewFile(file, true)
                        part.provider().copyTo(file.writeChannel())
                    }
                    else -> { }
                }
                part.dispose()
            }
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(fileName))
        }

        @JvmStatic
        suspend fun onRequestDownload(applicationCall: ApplicationCall) {
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
                    applicationCall.respondFile(file)
                }
                applicationCall.respondFile(file)
            } else {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("File not found", HttpStatusCode.NotFound, ""))
            }
        }
    }
}