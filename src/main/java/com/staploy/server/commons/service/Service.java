package com.staploy.server.commons.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.server.commons.packet.PacketProcessModel;
import com.staploy.server.commons.packet.PacketWrapper;
import com.staploy.server.routing.AdminProcess;
import com.staploy.server.routing.NodeProcess;
import io.ktor.http.HttpStatusCode;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.util.HashMap;

public class Service {
    private volatile static Service instance;
    private final Argument argument;
    public HashMap<String, PacketProcessModel> processModels;

    public interface onPacketProcessReplyReceiver {
        void onPacketReply(ApplicationCall call, HttpStatusCode code, String data);
    }

    public onPacketProcessReplyReceiver mOnPacketProcessReplyReceiver;

    private Service(Argument argument) {
        this.argument = argument;
    }

    public static synchronized void configureServiceInstance(Argument argument) {
        instance = new Service(argument);
        instance.processModels = new HashMap<>();
        instance.processModels.put(ServiceConsts.CONN_TYPE_NODE, new NodeProcess());
        instance.processModels.put(ServiceConsts.CONN_TYPE_ADMIN, new AdminProcess());
    }

    public static synchronized Service getInstance() {
        if (instance == null) {
            throw new NullPointerException("Service Instance is not initialized!");
        }
        return instance;
    }

    public static void invokeProcessWebSocketPacket(ApplicationCall applicationCall, String connectionType, DefaultWebSocketServerSession socketServerSession) throws Exception {
        instance.checkConnectionTypeOrThrow(connectionType);
        PacketProcessModel packetProcessModel = instance.processModels.get(connectionType);
        if (packetProcessModel != null) {
            packetProcessModel.onWebSocketSessionConnected(applicationCall, connectionType, socketServerSession);
        }
    }

    public static void invokeProcessPacket(ApplicationCall applicationCall, String connectionType, String rawData) throws Exception {
        instance.checkConnectionTypeOrThrow(connectionType);
        PacketProcessModel packetProcessModel = instance.processModels.get(connectionType);

        if (rawData == null || rawData.isEmpty()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("HTTP Request body is null", HttpStatusCode.Companion.getNoContent()));
        } else if (packetProcessModel != null) {
            packetProcessModel.onPacketReceived(applicationCall, connectionType, rawData);
        }
    }

    public static void replyPacket(ApplicationCall call, PacketWrapper data) throws InvalidProtocolBufferException {
        Service mInstance = Service.getInstance();
        if (mInstance != null && mInstance.mOnPacketProcessReplyReceiver != null) {
            mInstance.mOnPacketProcessReplyReceiver.onPacketReply(call, data.getStatusCode(), data.getSerializedData());
        }
    }

    public void checkConnectionTypeOrThrow(String connectionType) throws IllegalAccessException {
        if(!processModels.containsKey(connectionType)) {
            throw new IllegalAccessException(ServiceConsts.ERROR_CONN_TYPE_NOT_FOUND);
        }
    }

    public Argument getArgument() {
        return argument;
    }
}
