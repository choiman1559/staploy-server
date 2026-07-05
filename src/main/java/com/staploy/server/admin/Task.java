package com.staploy.server.admin;

import com.staploy.Admin;
import com.staploy.Protocol;
import com.staploy.Users;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.utils.WebSocketUtil;
import com.staploy.server.worker.WorkerManager;
import com.staploy.server.worker.WorkerProcess;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import org.jetbrains.annotations.Nullable;

public class Task {
    
    public record WorkerSession(String workerId, 
                                DefaultWebSocketServerSession webSocketServerSession,
                                WorkerManager.WorkerSessionInfo sessionInfo) {
        
    }

    public record AuthContext(boolean authValid, @Nullable Users.UserMetadata userMetadata) {
        public int getPermissionFlag() {
            if (authValid) {
                return userMetadata() != null ? userMetadata.getPermissions() : Users.PermissionFlag.SYSTEM_ADMIN_VALUE;
            }
            return Users.PermissionFlag.USERS_NONE_VALUE;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean matchPermission(Users.PermissionFlag requires) {
            final int uPermit = getPermissionFlag();
            return (uPermit & Users.PermissionFlag.SYSTEM_ADMIN_VALUE) != 0 || (uPermit & requires.getNumber()) == requires.getNumber();
        }

        public void matchPermissionThrows(Users.PermissionFlag requires) throws SecurityException {
            if(!matchPermission(requires)) {
                if(userMetadata == null) {
                    throw new SecurityException(String.format("Unauthorized user tried to access permission: %s", requires.name()));
                } else throw new SecurityException(String.format("User \"%s\" does not have permission: %s", userMetadata.getUserName(), requires.name()));
            }
        }
    }

    public interface OnWorkerReplyReceiver {
        void onReceive(Protocol.WorkerPacket workerPacket);
    }

    public void registerManagement(ApplicationCall applicationCall, Task.AuthContext authContext, Users.PermissionFlag permissionFlag, boolean recordAudit) throws SecurityException {
        if(recordAudit) {
            Helpers.getAuditDispatcher().attachFlags(applicationCall, permissionFlag);
        } else {
            Helpers.getAuditDispatcher().detachAudit(applicationCall);
        }
        authContext.matchPermissionThrows(permissionFlag);
    }

    public void registerManagement(ApplicationCall applicationCall, Task.AuthContext authContext, Users.PermissionFlag permissionFlag) throws SecurityException {
        registerManagement(applicationCall, authContext, permissionFlag, true);
    }
    
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        throw new RuntimeException("Stub!");
    }
    
    public void sendToWorker(String workerId, Protocol.ServerPacket serverPacket, OnWorkerReplyReceiver replyReceiver) throws NullPointerException {
        WorkerSession workerSession = getWorkerSessionById(workerId);
        if(serverPacket.getPacketInfo().getChallengeCode().isEmpty()) {
            throw new NullPointerException("ChallengeCode is null for packet: " + serverPacket.hashCode());
        }
        WorkerProcess.workerReplyReceiverMap.put(serverPacket.getPacketInfo().getChallengeCode(), replyReceiver);
        WebSocketUtil.replyWebSocket(workerSession.webSocketServerSession, serverPacket.toByteArray());
    }
    
    public WorkerSession getWorkerSessionById(String workerId) throws NullPointerException {
        DefaultWebSocketServerSession wsSession = WorkerProcess.workerSocketSession.get(workerId);
        if(wsSession == null) {
            throw new NullPointerException("Worker " + workerId + " does not alive");
        }
        return new WorkerSession(workerId, wsSession, Helpers.getWorkerManager().getWorkerSession(wsSession));
    }
}
