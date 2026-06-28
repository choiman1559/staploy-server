package com.staploy.server.admin;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Admin;
import com.staploy.Users;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.utils.Base64;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class AuditDispatcher implements InitHelperModule {

    private ConcurrentHashMap<ApplicationCall, AuditContext> auditContextMap;

    public record AuditContext(Users.AuditLogData.Builder auditLogData) {
        public static AuditContext createNew(Task.AuthContext authContext, Admin.RequestPacket requestPacket) {
            Users.AuditLogData.Builder auditBuilder = Users.AuditLogData.newBuilder();
            auditBuilder.setTimestamp(ZonedDateTime.now().toInstant().toEpochMilli());

            if(authContext.userMetadata() != null) {
                auditBuilder.setOperator(authContext.userMetadata().getUserName());
            } else {
                auditBuilder.setOperator(AdminConst.AUDIT_NO_USER);
            }

            auditBuilder.setRequest(Any.pack(requestPacket));
            return new AuditContext(auditBuilder);
        }

        public Users.AuditLogData pushResponse(PacketWrapper packetWrapper) {
            if(auditLogData.getAction() == null) {
                auditLogData.setAction(Users.PermissionFlag.NONE);
            }
            return auditLogData().setResponse(Any.pack(packetWrapper.getResponsePacket())).build();
        }
    }

    public void createNew(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, Task.AuthContext authContext) {
        auditContextMap.put(applicationCall, AuditContext.createNew(authContext, requestPacket));
    }

    public void attachFlags(ApplicationCall applicationCall, Users.PermissionFlag permissionFlag) {
        if(auditContextMap.containsKey(applicationCall)) {
            AuditContext auditContext = auditContextMap.get(applicationCall);
            if (auditContext != null) {
                auditContext.auditLogData().setAction(permissionFlag);
            }
        }
    }

    public void commitAudit(ApplicationCall applicationCall, PacketWrapper response) {
        if(auditContextMap.containsKey(applicationCall)) {
            AuditContext auditContext = auditContextMap.get(applicationCall);
            if(auditContext != null) {
                Users.AuditLogData auditLogData = auditContext.pushResponse(response);
                Helpers.getPersistsHelper().getRedisCommands().lpush(AdminConst.SCHEMA_USER_AUDIT, Base64.encode(auditLogData.toByteArray()));
            }
            auditContextMap.remove(applicationCall);
        }
    }

    public ArrayList<Users.AuditLogData> queryLogs() throws InvalidProtocolBufferException {
        ArrayList<Users.AuditLogData> auditLogData = new ArrayList<>();
        List<String> rawDatas = Helpers.getPersistsHelper().getRedisCommands().lrange(AdminConst.SCHEMA_USER_AUDIT, 0, -1);

        for (String data : rawDatas) {
            auditLogData.add(Users.AuditLogData.parseFrom(Base64.decode(data)));
        }
        return auditLogData;
    }

    @Override
    public void onServiceAttache() {
        auditContextMap = new ConcurrentHashMap<>();
    }

    @Override
    public void onServiceDetache() {

    }
}
