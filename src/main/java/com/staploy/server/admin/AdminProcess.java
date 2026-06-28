package com.staploy.server.admin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.Users;
import com.staploy.server.admin.tasks.*;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketProcessModel;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.util.HashMap;

public class AdminProcess implements PacketProcessModel {

    public HashMap<Admin.TaskGroup, Task> taskGroupMap;

    public AdminProcess() {
        taskGroupMap = new HashMap<>();
        taskGroupMap.put(Admin.TaskGroup.TASK_MANAGE_APPS, new AppsTask());
        taskGroupMap.put(Admin.TaskGroup.TASK_MANAGE_NODE, new NodeTask());
        taskGroupMap.put(Admin.TaskGroup.TASK_DEPLOY, new DeployTask());
        taskGroupMap.put(Admin.TaskGroup.TASK_GROUP, new GroupTask());
        taskGroupMap.put(Admin.TaskGroup.TASK_USER, new UserTask());
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        try {
            Admin.RequestPacket requestPacket = parseRequestPacket(rawData);
            if (!checkValidAuth(applicationCall, requestPacket)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.ERROR_TOKEN_NOT_VALID));
                return;
            }

            taskGroupMap.get(requestPacket.getTaskGroup()).performTask(applicationCall, requestPacket);
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

    }

    private boolean checkValidAuth(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        if(requestPacket.getTaskGroup() == Admin.TaskGroup.TASK_USER
                && requestPacket.hasUserTaskType() && requestPacket.getUserTaskType().getUserTaskTypes() == Users.TaskUserTypes.TYPE_USER_LOGIN) {
            return true;
        }

        String token = applicationCall.getRequest().getHeaders().get(AdminConst.HEADER_KEY_TOKEN);
        if (!Service.getInstance().getArgument().allowNonUser && (token == null || token.isEmpty())) {
            return false;
        }

        if (token != null && token.startsWith("Bearer ")) {
            token = token.replace("Bearer ", "").trim();
            DecodedJWT decodedJWT = JWT.decode(token);

            try {
                JWT.require(Helpers.getJwtCertManager().getRsaKey())
                        .withAudience(Service.getInstance().getServerUUID())
                        .withIssuer(Service.getInstance().getArgument().host)
                        .build().verify(decodedJWT);
            } catch (Exception e) {
                return false;
            }

            String uuid = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_UUID).asString();
            String username = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_USERNAME).asString();
            Long version = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_VERSION).asLong();
            Integer permission = decodedJWT.getClaim(ServiceConsts.JWT_CLAIM_PERMISSION).asInt();

            UserPersistent userPersistent = UserPersistent.fromUserName(username);
            if (!userPersistent.hasUser() || !userPersistent.uuid().equals(uuid)) {
                return false;
            }

            Users.UserMetadata userMetadata = userPersistent.getMetadata();
            return userMetadata != null && (userMetadata.getVersion() == version && userMetadata.getPermissions() == permission);
        }

        return Service.getInstance().getArgument().allowNonUser;
    }

    private Admin.RequestPacket parseRequestPacket(String rawData) throws InvalidProtocolBufferException {
        Admin.RequestPacket.Builder reqBuilder = Admin.RequestPacket.newBuilder();
        JsonFormat.parser().merge(rawData, reqBuilder);
        return reqBuilder.build();
    }
}
