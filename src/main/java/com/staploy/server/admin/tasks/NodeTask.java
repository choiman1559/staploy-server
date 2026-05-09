package com.staploy.server.admin.tasks;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Service;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

public class NodeTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) {
        switch (requestPacket.getNodeTaskType()) {
            case TYPE_NODE_CONNECTED -> {

            }

            case TYPE_NODE_REQ_WORKER_INFO -> {

            }

            case TYPE_NODE_REQ_APP_INFO -> {
                Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();
                serverPacket.setPacketInfo(PacketWrapper.createNewPacket(Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK, Protocol.ActionProcedure.PROCEDURE_REQUEST_APP_INFO));
                serverPacket.addAllAppInfoFetch(requestPacket.getAppInfoList());

                sendToWorker(requestPacket.getWorker(0).getWorkerId(), serverPacket.build(), workerPacket -> {
                    try {
                        Service.replyPacket(applicationCall, PacketWrapper.makePacket(JsonFormat.printer().print(workerPacket)));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            case TYPE_NODE_EXECUTE_SHELL -> {

            }

            case TYPE_NODE_DISCONN_WORKER -> {

            }
        }
    }
}
