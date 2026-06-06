package com.staploy.server.admin;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.server.admin.tasks.AppsTask;
import com.staploy.server.admin.tasks.DeployTask;
import com.staploy.server.admin.tasks.GroupTask;
import com.staploy.server.admin.tasks.NodeTask;
import com.staploy.server.commons.service.Service;
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
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        try {
            Admin.RequestPacket requestPacket = parseRequestPacket(rawData);
            taskGroupMap.get(requestPacket.getTaskGroup()).performTask(applicationCall, requestPacket);
        } catch (Exception e) {
            if(Service.getInstance().getArgument().isDebug) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
            }
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(e.getMessage()));
        }
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) {

    }

    private Admin.RequestPacket parseRequestPacket(String rawData) throws InvalidProtocolBufferException {
        Admin.RequestPacket.Builder reqBuilder = Admin.RequestPacket.newBuilder();
        JsonFormat.parser().merge(rawData, reqBuilder);
        return reqBuilder.build();
    }
}
