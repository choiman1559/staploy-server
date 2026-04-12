package com.staploy.server.routing;

import com.staploy.server.commons.packet.PacketProcessModel;
import com.staploy.server.commons.utils.Log;
import com.staploy.server.commons.utils.WebSocketUtil;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

public class NodeProcess implements PacketProcessModel {
    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {

    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception {
        WebSocketUtil.registerOnDataIncomeSocket(socketServerSession, data -> {
            Log.printDebug("dddd", new String(data));
        });
        WebSocketUtil.replyWebSocket(socketServerSession, "hello world!");
    }
}
