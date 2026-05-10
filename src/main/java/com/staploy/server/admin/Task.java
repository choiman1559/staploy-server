package com.staploy.server.admin;

import com.staploy.Admin;
import com.staploy.Protocol;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.utils.WebSocketUtil;
import com.staploy.server.worker.WorkerManager;
import com.staploy.server.worker.WorkerProcess;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

public class Task {
    
    public record WorkerSession(String workerId, 
                                DefaultWebSocketServerSession webSocketServerSession,
                                WorkerManager.WorkerSessionInfo sessionInfo) {
        
    }

    public interface OnWorkerReplyReceiver {
        void onReceive(Protocol.WorkerPacket workerPacket);
    }
    
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        throw new RuntimeException("Stub!");
    }
    
    public void sendToWorker(String workerId, Protocol.ServerPacket serverPacket, OnWorkerReplyReceiver replyReceiver) {
        WorkerSession workerSession = getWorkerSessionById(workerId);
        WorkerProcess.workerReplyReceiverMap.put(serverPacket.getPacketInfo().getChallengeCode(), replyReceiver);
        WebSocketUtil.replyWebSocket(workerSession.webSocketServerSession, serverPacket.toByteArray());
    }
    
    public WorkerSession getWorkerSessionById(String workerId) {
        DefaultWebSocketServerSession wsSession = WorkerProcess.workerSocketSession.get(workerId);
        return new WorkerSession(workerId, wsSession, Helpers.getWorkerManager().getWorkerSession(wsSession));
    }
}
