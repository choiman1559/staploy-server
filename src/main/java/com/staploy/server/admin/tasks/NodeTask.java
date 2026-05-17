package com.staploy.server.admin.tasks;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import com.staploy.server.worker.WorkerProcess;
import io.ktor.server.application.ApplicationCall;

import java.util.ArrayList;
import java.util.HashSet;

public class NodeTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        switch (requestPacket.getNodeTaskType()) {
            case TYPE_NODE_CONNECTED -> {
                ArrayList<Protocol.WorkerPacket> workerPackets = new ArrayList<>();
                HashSet<String> requestIds = new HashSet<>();

                for(Protocol.WorkerInfo workerInfo : requestPacket.getWorkerList()) {
                    requestIds.add(workerInfo.getWorkerId());
                }

                for(String id : WorkerProcess.workerSocketSession.keySet()) {
                    if(requestIds.isEmpty() || requestIds.contains(id)) {
                        WorkerSession workerSession = getWorkerSessionById(id);
                        workerPackets.add(Protocol.WorkerPacket.newBuilder().setWorkerInfo(workerSession.sessionInfo().getWorkerInfo()).build());
                    }
                }

                try {
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPackets));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }

            case TYPE_NODE_REQ_WORKER_INFO -> {
                String workerId = requestPacket.getWorker(0).getWorkerId();
                WorkerSession workerSession = getWorkerSessionById(workerId);
                Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
                serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_REQUEST_WORKER_INFO));

                sendToWorker(workerId, serverPacket.build(), workerPacket -> {
                    workerSession.sessionInfo().registerWorker(workerPacket.getWorkerInfo());
                    try {
                        Service.replyPacket(applicationCall, PacketWrapper.makePacket("",
                                Protocol.WorkerPacket.newBuilder().setWorkerInfo(
                                                workerSession.sessionInfo().getWorkerPersists().getWorkerInfo())
                                        .build()));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            case TYPE_NODE_REQ_APP_INFO -> {
                Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
                serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_REQUEST_APP_INFO));
                serverPacket.addAllAppInfoFetch(requestPacket.getAppInfoFetchList());

                sendToWorker(requestPacket.getWorker(0).getWorkerId(), serverPacket.build(), workerPacket -> {
                    try {
                        Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPacket));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            case TYPE_NODE_EXECUTE_SHELL -> {
                Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
                serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_EXECUTE_SHELL));
                serverPacket.addAppInfoFetch(App.AppInfoFetch.newBuilder().setApp(App.AppInfo.newBuilder().setAppName(requestPacket.getExtraData())).build());

                sendToWorker(requestPacket.getWorker(0).getWorkerId(), serverPacket.build(), workerPacket -> {
                    try {
                        Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPacket));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            case TYPE_NODE_DISCONN_WORKER -> {
                WorkerSession workerSession = getWorkerSessionById(requestPacket.getWorker(0).getWorkerId());
                WorkerProcess.cleanUpSocket(workerSession.webSocketServerSession());

                try {
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
