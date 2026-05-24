package com.staploy.server.admin.tasks;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Admin;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.pkg.PersistsPkg;
import com.staploy.server.commons.service.Service;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

public class DeployTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        switch (requestPacket.getDeployTaskType()) {
            case TYPE_DEPLOY_NONE -> {

            }

            case TYPE_DEPLOY_PUSH_VERSION -> deployApp(applicationCall, requestPacket);

            case TYPE_DEPLOY_SET_VERSION -> setTriggerApp(applicationCall, requestPacket);

            case TYPE_DEPLOY_DEL_VERSION -> removeApp(applicationCall, requestPacket);
        }
    }

    private void deployApp(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        Protocol.WorkerInfo workerInfo = requestPacket.getWorker(0);
        Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();

        serverPacket.setPacketInfo(PacketWrapper.createNewPacket(
                Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK,
                Protocol.ActionProcedure.PROCEDURE_ADD_APP_VERSION,
                PersistsPkg.getPackageTokenId(PersistsPkg.getCpuArchByWorker(workerInfo), requestPacket.getAppInfoFetch(0))
        ));

        // TODO: Store & Load appDescription from DB
        serverPacket.addAllAppInfoFetch(requestPacket.getAppInfoFetchList());
        sendToWorker(workerInfo.getWorkerId(), serverPacket.build(), workerPacket -> {
            try {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPacket));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void setTriggerApp(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
        serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_SET_APP_VERSION));
        serverPacket.addAllAppInfoFetch(requestPacket.getAppInfoFetchList());

        sendToWorker(requestPacket.getWorker(0).getWorkerId(), serverPacket.build(), workerPacket -> {
            try {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPacket));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void removeApp(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
        serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_DELETE_APP_VERSION));
        serverPacket.addAllAppInfoFetch(requestPacket.getAppInfoFetchList());

        sendToWorker(requestPacket.getWorker(0).getWorkerId(), serverPacket.build(), workerPacket -> {
            try {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket("", workerPacket));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
