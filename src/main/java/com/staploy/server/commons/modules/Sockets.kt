package com.staploy.server.commons.modules

import com.staploy.server.commons.service.Service
import com.staploy.server.commons.service.ServiceConsts
import com.staploy.server.commons.utils.Log
import com.staploy.server.commons.utils.WebSocketUtil
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

const val LOG_TAG = "WebSocket"

@Suppress("unused")
fun Application.configureSockets() {

    Log.print(LOG_TAG, "Websocket Enabled!!!")
    install(WebSockets) {
        pingPeriod = 20.toDuration(DurationUnit.SECONDS)
        timeout = 30.toDuration(DurationUnit.SECONDS)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket(ServiceConsts.API_ROUTE_SCHEMA) {
            if (call.parameters["version"] == "v1") {
                val connectionType: String = call.parameters["connection_type"].toString()
                val socketSession = this

                try {
                    Service.getInstance().checkConnectionTypeOrThrow(connectionType)
                    CoroutineScope(Dispatchers.IO).launch {
                        Log.printDebug(LOG_TAG, "Connected: $socketSession")
                        Service.invokeProcessWebSocketPacket(call, connectionType, socketSession)
                    }

                    for (frame in incoming) {
                        //Log.printDebug(LOG_TAG, "Incoming: " + String(frame.readBytes())) // Remain this line for debugging purpose
                        val listener = WebSocketUtil.getSocketFrameIncomeListener(socketSession)
                        listener?.onIncoming(frame.readBytes())
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        Log.printDebug(LOG_TAG, "Disconnected: $socketSession")
                        WebSocketUtil.getSocketDisconnectListener(socketSession)?.onDisconnect()
                        WebSocketUtil.cleanUpSocket(socketSession)
                    }
                } catch (e: Exception) {
                    WebSocketUtil.closeWebSocket(
                        this,
                        CloseReason.Codes.PROTOCOL_ERROR,
                        ServiceConsts.ERROR_CONN_TYPE_NOT_FOUND
                    )
                }
            } else {
                WebSocketUtil.closeWebSocket(
                    this,
                    CloseReason.Codes.PROTOCOL_ERROR,
                    ServiceConsts.ERROR_ILLEGAL_ARGUMENT
                )
            }
        }
    }
}