package com.staploy.server.admin;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.server.commons.service.Service;
import com.staploy.server.packet.PacketProcessModel;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

public class AdminProcess implements PacketProcessModel {
    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        try {
            Admin.RequestPacket requestPacket = parseRequestPacket(rawData);
        } catch (Exception e) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Request body cannot be parsed"));
        }
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception {

    }

    private Admin.RequestPacket parseRequestPacket(String rawData) throws InvalidProtocolBufferException {
        Admin.RequestPacket.Builder reqBuilder = Admin.RequestPacket.newBuilder();
        JsonFormat.parser().merge(rawData, reqBuilder);
        return reqBuilder.build();
    }
}
