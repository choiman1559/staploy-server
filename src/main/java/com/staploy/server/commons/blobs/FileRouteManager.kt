package com.staploy.server.commons.blobs

import com.staploy.server.commons.service.Helpers
import com.staploy.server.commons.service.InitHelperModule
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.service.ServiceConsts
import kotlinx.io.IOException

import java.io.File
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.isSymbolicLink
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class FileRouteManager : InitHelperModule {

    override fun onServiceAttache() {

    }

    override fun onServiceDetache() {

    }

    fun registerActualFile(file: File, hardLink: Boolean): String {
        val token = registerNewUpload(file.name)
        val targetFile = getBlobFile(token)

        if(hardLink) {
            file.copyTo(targetFile, overwrite = true)
        } else {
            targetFile.toPath().createSymbolicLinkPointingTo(file.toPath())
        }
        return token
    }

    fun registerNewUpload(originalName: String): String {
        val newName = generateNewFileToken()
        Helpers.getPersistsHelper().redisCommands
            .hset(ServiceConsts.SCHEMA_BLOB_LIST, newName, originalName)
        return newName
    }

    fun removeBlob(token: String) {
        val blobFile = getBlobFile(token)
        val isSymlink = blobFile.toPath().isSymbolicLink()

        if(isSymlink) {
            try {
                val realFile = blobFile.toPath().toRealPath().toFile()
                if (realFile.exists()) {
                    realFile.delete()
                }
            } catch (_: IOException) {
                //Real-file not exists, continue;
            }
        }

        if ((isSymlink || blobFile.exists()) && blobFile.delete()) {
            Helpers.getPersistsHelper().redisCommands
                .hdel(ServiceConsts.SCHEMA_BLOB_LIST, token)
        } else throw FileSystemException(blobFile, null, "File cannot be deleted")
    }

    fun getBlobFile(token: String): File {
        val argument = Service.getInstance().argument
        return File(argument.baseDir, ServiceConsts.PATH_BLOB_DIR + token)
    }

    fun getActualName(token: String): String? {
        return Helpers.getPersistsHelper().redisCommands.hget(ServiceConsts.SCHEMA_BLOB_LIST, token)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateNewFileToken(): String {
        return Uuid.random().toString()
    }
}