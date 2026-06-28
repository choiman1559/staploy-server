package com.staploy.server.admin.tasks;

import com.staploy.Admin;
import com.staploy.Protocol;
import com.staploy.Users;
import com.staploy.server.admin.AdminConst;
import com.staploy.server.admin.GroupPersistent;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import com.staploy.server.worker.WorkerPersists;
import io.ktor.server.application.ApplicationCall;

import java.util.ArrayList;

public class GroupTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        if (!requestPacket.hasGroupTaskType()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, ServiceConsts.ERROR_ILLEGAL_ARGUMENT));
            return;
        }

        Admin.GroupRequestPacket groupRequestPacket = requestPacket.getGroupTaskType();
        userContext.matchPermissionThrows(Users.PermissionFlag.GROUP_MANAGE);

        switch (groupRequestPacket.getGroupTaskTypes()) {
            case TYPE_GROUP_CREATE -> {
                if (!groupRequestPacket.hasGroupName() || groupRequestPacket.getGroupName().isBlank()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group name tag is blank"));
                } else {
                    GroupPersistent groupPersistent = GroupPersistent.getInstance(groupRequestPacket.getGroupName());
                    if (groupPersistent.hasGroup()) {
                        Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group " + groupRequestPacket.getGroupName() + " already exists"));
                        return;
                    }

                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                            Admin.GroupResponsePacket.newBuilder()
                                    .setRequestedName(groupRequestPacket.getGroupName())
                                    .setGroupName(groupPersistent.createGroup())
                                    .build()));
                }
            }

            case TYPE_GROUP_DELETE -> {
                if (!groupRequestPacket.hasGroupName() || groupRequestPacket.getGroupName().isBlank()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group name tag is blank"));
                } else {
                    GroupPersistent groupPersistent = GroupPersistent.getInstance(groupRequestPacket.getGroupName());
                    if (!groupPersistent.hasGroup()) {
                        Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group " + groupRequestPacket.getGroupName() + " not exists"));
                        return;
                    }

                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                            Admin.GroupResponsePacket.newBuilder()
                                    .setRequestedName(groupRequestPacket.getGroupName())
                                    .setGroupName(groupPersistent.deleteGroup())
                                    .build()));
                }
            }

            case TYPE_GROUP_ADD_WORKER -> {
                if (!groupRequestPacket.hasGroupName() || groupRequestPacket.getGroupName().isBlank()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group name tag is blank"));
                } else {
                    GroupPersistent groupPersistent = GroupPersistent.getInstance(groupRequestPacket.getGroupName());
                    if (!groupPersistent.hasGroup()) {
                        Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group " + groupRequestPacket.getGroupName() + " not exists"));
                        return;
                    }

                    groupPersistent.addWorkers(groupRequestPacket.getNamesList());
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                            Admin.GroupResponsePacket.newBuilder()
                                    .setGroupName(groupRequestPacket.getGroupName())
                                    .build()));
                }
            }

            case TYPE_GROUP_REMOVE_WORKER -> {
                if (!groupRequestPacket.hasGroupName() || groupRequestPacket.getGroupName().isBlank()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group name tag is blank"));
                    return;
                }

                GroupPersistent groupPersistent = GroupPersistent.getInstance(groupRequestPacket.getGroupName());
                if (!groupPersistent.hasGroup()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group " + groupRequestPacket.getGroupName() + " not exists"));
                    return;
                }

                groupPersistent.removeWorkers(groupRequestPacket.getNamesList());
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                        Admin.GroupResponsePacket.newBuilder()
                                .setGroupName(groupRequestPacket.getGroupName())
                                .build()));
            }

            case TYPE_QUERY_GROUP_LIST -> {
                if (!groupRequestPacket.hasGroupName() || groupRequestPacket.getGroupName().isBlank()) {
                    ArrayList<Admin.GroupResponsePacket> groupResponsePackets = new ArrayList<>();
                    for(String groupName : GroupPersistent.getAllGroups()) {
                        groupResponsePackets.add(Admin.GroupResponsePacket.newBuilder()
                                        .setGroupName(groupName)
                                        .setWorkerInfo(Protocol.WorkerInfo.newBuilder()
                                                .setCpuCoreCount(GroupPersistent.getInstance(groupName).getWorkerList().size())
                                                .build())
                                .build());
                    }
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, null, groupResponsePackets, null));
                    return;
                }

                GroupPersistent groupPersistent = GroupPersistent.getInstance(groupRequestPacket.getGroupName());
                if (!groupPersistent.hasGroup()) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Group " + groupRequestPacket.getGroupName() + " not exists"));
                    return;
                }

                ArrayList<Admin.GroupResponsePacket> packetArrayList = new ArrayList<>();
                for(Protocol.WorkerInfo workerInfo : groupPersistent.getWorkerList()) {
                    packetArrayList.add(Admin.GroupResponsePacket.newBuilder()
                            .setWorkerInfo(workerInfo)
                            .setGroupName(groupRequestPacket.getGroupName())
                            .build());
                }
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, null, packetArrayList, null));
            }

            case TYPE_QUERY_WORKER_IDS -> {
                if(groupRequestPacket.getNamesCount() < 1) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Names entry is empty"));
                    return;
                }

                ArrayList<Admin.GroupResponsePacket> packetArrayList = new ArrayList<>();
                for(String rawName : groupRequestPacket.getNamesList()) {
                    if(rawName.startsWith(AdminConst.PREFIX_QUERY_GROUP)) {
                        GroupPersistent groupPersistent = GroupPersistent.getInstance(rawName.replace(AdminConst.PREFIX_QUERY_GROUP, ""));
                        if(groupPersistent.hasGroup()) {
                            for (Protocol.WorkerInfo workerInfo : groupPersistent.getWorkerList()) {
                                Task.WorkerSession workerSession = getWorkerSessionById(workerInfo.getWorkerId());
                                packetArrayList.add(Admin.GroupResponsePacket.newBuilder()
                                        .setRequestedName(rawName)
                                        .setGroupName(groupPersistent.getGroupName())
                                        .setIsAlive(workerSession != null && workerSession.sessionInfo().isActive())
                                        .setWorkerInfo(workerInfo)
                                        .build());
                            }
                        } else {
                            packetArrayList.add(Admin.GroupResponsePacket.newBuilder()
                                    .setRequestedName(groupPersistent.getGroupName())
                                    .setGroupName(groupPersistent.getGroupName())
                                    .build());
                        }
                        continue;
                    }

                    String nameOrIdResult = GroupPersistent.findWorkerId(rawName);
                    if(nameOrIdResult.isBlank()) {
                        packetArrayList.add(Admin.GroupResponsePacket.newBuilder()
                                .setRequestedName(rawName).build());
                    } else {
                        Task.WorkerSession workerSession = getWorkerSessionById(nameOrIdResult);
                        packetArrayList.add(Admin.GroupResponsePacket.newBuilder()
                                .setRequestedName(rawName)
                                .setIsAlive(workerSession != null && workerSession.sessionInfo().isActive())
                                .setWorkerInfo(Protocol.WorkerInfo.newBuilder()
                                        .setWorkerId(nameOrIdResult)
                                        .setWorkerName(new WorkerPersists(nameOrIdResult).getWorkerName())
                                        .build())
                                .build());
                    }
                }
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, null, packetArrayList, null));
            }
        }
    }
}
