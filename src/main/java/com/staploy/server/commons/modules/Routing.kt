package com.staploy.server.commons.modules

import com.staploy.server.packet.PacketWrapper
import com.staploy.server.commons.service.Service
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.commons.utils.Log

import io.ktor.server.application.*
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

suspend fun doProcessPacket(call: ApplicationCall) {
    if (call.parameters["version"] == "v1") {
        val connectionType: String = call.parameters["connection_type"].toString()
        try {
            Service.invokeProcessPacket(call, connectionType, call.receiveText())
        } catch (_: Exception) {
            Service.replyPacket(
                call, PacketWrapper.makeErrorPacket(
                    ServiceConsts.ERROR_CONN_TYPE_NOT_FOUND
                )
            )
        }
    } else {
        Service.replyPacket(
            call, PacketWrapper.makeErrorPacket(
                ServiceConsts.ERROR_ILLEGAL_ARGUMENT
            )
        )
    }
}

fun Application.configureRouting() {
    val service: Service = Service.getInstance()
    service.mOnPacketProcessReplyReceiver = Service.onPacketProcessReplyReceiver { call, code, data ->
        runBlocking {
            call.respond(code, data)
            Log.printDebug("routingProcess", String.format("RESPONSE %s", data))
        }
    }

    routing {
        post(ServiceConsts.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        get(ServiceConsts.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        delete(ServiceConsts.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        put(ServiceConsts.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }
    }
}
