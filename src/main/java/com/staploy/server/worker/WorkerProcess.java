package com.staploy.server.worker;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.packet.PacketProcessModel;
import com.staploy.server.commons.utils.Log;
import com.staploy.server.commons.utils.WebSocketUtil;
import com.staploy.server.packet.PacketWrapper;

import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class WorkerProcess implements PacketProcessModel {

    private static final String LogTAG = "WorkerProcess";
    public static final ConcurrentHashMap<String, DefaultWebSocketServerSession> workerSocketSession = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Task.OnWorkerReplyReceiver> workerReplyReceiverMap = new ConcurrentHashMap<>();

    private record ReceivePacketBundle(
            ApplicationCall applicationCall,
            DefaultWebSocketServerSession socketServerSession,
            Protocol.WorkerPacket workerPacket
    ) { }

    private void invokeWorkerReplyReceiver(Protocol.WorkerPacket workerPacket) {
        String mapKey = workerPacket.getPacketInfo().getChallengeCode();
        if(workerReplyReceiverMap.containsKey(mapKey)) {
            workerReplyReceiverMap.get(mapKey).onReceive(workerPacket);
            workerReplyReceiverMap.remove(mapKey);
        }
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) {

    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) {
        WebSocketUtil.registerOnDisconnectSocket(socketServerSession, () -> cleanUpSocket(socketServerSession));
        WebSocketUtil.registerOnDataIncomeSocket(socketServerSession, data -> {
            Log.printDebug(LogTAG, String.format("New bytestream (%d) incoming => %s", Arrays.hashCode(data), new String(data)));
            preProcessPacket(applicationCall, socketServerSession, data);
        });

        WorkerManager.WorkerSessionInfo workerSessionInfo = Helpers.getWorkerManager().getWorkerSession(socketServerSession);
        if (workerSessionInfo == null) {
            Log.printDebug(LogTAG, "New incoming worker encountered, sending empty hello packet");
            WebSocketUtil.replyWebSocket(socketServerSession, PacketWrapper.createNewServerPacket(
                    PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_SERVER_HELLO, Protocol.ActionProcedure.PROCEDURE_NONE).build(),
                    null
            ).build().toByteArray());
        }
    }

    public static void cleanUpSocket(DefaultWebSocketServerSession socketServerSession) {
        WorkerManager.WorkerSessionInfo workerSessionInfo = Helpers.getWorkerManager().getWorkerSession(socketServerSession);
        if (workerSessionInfo != null && !workerSessionInfo.getWorkerUUID().isEmpty()) {
            workerSocketSession.remove(workerSessionInfo.getWorkerUUID());
        }
        if (workerSessionInfo != null && workerSessionInfo.isActive()) {
            Helpers.getWorkerManager().detachWorkerSession(socketServerSession);
        }
    }

    private void preProcessPacket(ApplicationCall applicationCall, DefaultWebSocketServerSession socketServerSession, byte[] data) {
        try {
            Protocol.WorkerPacket workerPacket = Protocol.WorkerPacket.parseFrom(data);
            ReceivePacketBundle receivePacketBundle = new ReceivePacketBundle(applicationCall, socketServerSession, workerPacket);
            routePacket(receivePacketBundle);
        } catch (IOException e) {
            Log.print(LogTAG, String.format("Error: Cannot parse from raw data (%d)", Arrays.hashCode(data)));
        }
    }

    private void routePacket(ReceivePacketBundle packetBundle) {
        WorkerManager.WorkerSessionInfo workerSessionInfo = Helpers.getWorkerManager().getWorkerSession(packetBundle.socketServerSession);
        if (workerSessionInfo == null
                && packetBundle.workerPacket.getPacketInfo().getProcedure() != Protocol.ProtocolProcedure.PROCEDURE_SERVER_HELLO) {
            WebSocketUtil.closeWebSocket(packetBundle.socketServerSession, CloseReason.Codes.PROTOCOL_ERROR, "");
            return;
        }

        switch (packetBundle.workerPacket.getPacketInfo().getProcedure()) {
            case PROCEDURE_SERVER_HELLO -> {
                if (workerSessionInfo != null && packetBundle.workerPacket.hasWorkerInfo()) {
                    workerSessionInfo.registerWorker(packetBundle.workerPacket.getWorkerInfo());
                    Log.printDebug(LogTAG, "Registered new device: " + packetBundle.workerPacket.getWorkerInfo().getWorkerId());
                    finalizeHandshake(workerSessionInfo, packetBundle);
                } else {
                    Log.printDebug(LogTAG, "Receiving empty ACK packet, checking already registered worker");
                    workerSessionInfo = Helpers.getWorkerManager().registerNewSession(packetBundle.socketServerSession, packetBundle.workerPacket.getWorkerInfo());
                    if (workerSessionInfo.isActive()) {
                        WebSocketUtil.closeWebSocket(packetBundle.socketServerSession, CloseReason.Codes.TRY_AGAIN_LATER, "");
                        return;
                    }

                    if (workerSessionInfo.isRegistered()) {
                        Protocol.WorkerInfo workerInfo = workerSessionInfo.getWorkerInfo(true);
                        try {
                            Log.printDebug(LogTAG, "Handshake completed: " + JsonFormat.printer().print(workerInfo));
                            finalizeHandshake(workerSessionInfo, packetBundle);
                        } catch (InvalidProtocolBufferException e) {
                            Log.printDebug(LogTAG, "Handshake completed but message not decoded...");
                            WebSocketUtil.closeWebSocket(packetBundle.socketServerSession, CloseReason.Codes.INTERNAL_ERROR, "");
                        }
                    } else {
                        Log.printDebug(LogTAG, "Non-data for this worker, Requesting full-worker info for new registration");
                        WebSocketUtil.replyWebSocket(packetBundle.socketServerSession, PacketWrapper.createNewServerPacket(
                                PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_SERVER_HELLO, Protocol.ActionProcedure.PROCEDURE_REQUEST_WORKER_INFO).build(),
                                null
                        ).build().toByteArray());
                    }
                }
            }

            case PROCEDURE_REQUEST_TASK -> invokeWorkerReplyReceiver(packetBundle.workerPacket);

            case PROCEDURE_CHECK_TASK -> {

            }

            case PROCEDURE_CANCEL_TASK -> {
                //TODO: STUB!!! implement cancel task (on both backend & worker)
            }
        }
    }

    private void finalizeHandshake(WorkerManager.WorkerSessionInfo workerSessionInfo, ReceivePacketBundle receivePacketBundle) {
        workerSocketSession.put(workerSessionInfo.getWorkerUUID(), receivePacketBundle.socketServerSession);
        WebSocketUtil.replyWebSocket(receivePacketBundle.socketServerSession, PacketWrapper.createNewServerPacket(
                PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_SERVER_HELLO, Protocol.ActionProcedure.PROCEDURE_ACK).build(),
                null
        ).build().toByteArray());
    }
}
