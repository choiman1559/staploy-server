package com.staploy.server.registry;

import com.staploy.Admin;
import com.staploy.server.admin.AdminProcess;
import com.staploy.server.admin.JwtCertManager;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.commons.utils.WebSocketUtil;
import com.staploy.server.packet.PacketProcessModel;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;

public class RegistryProcess implements PacketProcessModel {

    private final static RegistryProvider registryProvider = new RegistryProvider();

    public static class RegistryStub extends RegistryProcess {
        @Override
        public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
            throw new IllegalAccessException("Registry service not enabled on this server");
        }
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        try {
            Admin.RequestPacket requestPacket = AdminProcess.parseRequestPacket(rawData);
            Task.AuthContext authContext = JwtCertManager.checkValidAuth(applicationCall, requestPacket);
            Helpers.getAuditDispatcher().createNew(applicationCall, requestPacket, authContext);

            if (authContext.userMetadata() != null && !authContext.authValid()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.ERROR_TOKEN_NOT_VALID));
                return;
            }

            if(!requestPacket.hasRegistryTaskType()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Invalid registry packet"));
            }

            registryProvider.performTask(applicationCall, requestPacket, authContext);
        } catch (Exception e) {
            if (Service.getInstance().getArgument().isDebug) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(e.getMessage()));
        }
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) {
        WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, "Not valid service");
    }
}
