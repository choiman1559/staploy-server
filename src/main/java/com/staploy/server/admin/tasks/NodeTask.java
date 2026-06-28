package com.staploy.server.admin.tasks;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.Users;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import com.staploy.server.worker.WorkerProcess;
import io.ktor.server.application.ApplicationCall;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class NodeTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) {
        switch (requestPacket.getNodeTaskType()) {
            case TYPE_NODE_CONNECTED -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
                ArrayList<Protocol.WorkerPacket> workerPackets = new ArrayList<>();
                HashSet<String> requestIds = new HashSet<>();

                for(Protocol.WorkerInfo workerInfo : requestPacket.getWorkerList()) {
                    requestIds.add(workerInfo.getWorkerId());
                }

                for(String id : WorkerProcess.workerSocketSession.keySet()) {
                    if(requestIds.isEmpty() || requestIds.contains(id)) {
                        WorkerSession workerSession = getWorkerSessionById(id);

                        Protocol.WorkerInfo.Builder workerInfo = Protocol.WorkerInfo.newBuilder(workerSession.sessionInfo().getWorkerInfo());
                        if(!Objects.equals(Helpers.getWorkerManager().getWorkerIdByName(workerInfo.getWorkerName()), id)) {
                            workerInfo.setWorkerName(workerInfo.getWorkerName() + " (Duplicated)");
                        }
                        workerPackets.add(Protocol.WorkerPacket.newBuilder().setWorkerInfo(workerInfo).build());
                    }
                }

                try {
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPackets));
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException(e);
                }
            }

            case TYPE_NODE_REQ_WORKER_INFO -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
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
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
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
                registerManagement(applicationCall, userContext, Users.PermissionFlag.NODE_BASH);
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
                registerManagement(applicationCall, userContext, Users.PermissionFlag.NODE_DISCONN);
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
