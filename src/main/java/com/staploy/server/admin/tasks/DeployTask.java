package com.staploy.server.admin.tasks;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.Users;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.pkg.AppPersists;
import com.staploy.server.admin.pkg.PersistsPkg;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

public class DeployTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws InvalidProtocolBufferException {
        switch (requestPacket.getDeployTaskType()) {
            case TYPE_DEPLOY_NONE -> Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, ServiceConsts.ERROR_ILLEGAL_ARGUMENT));

            case TYPE_DEPLOY_PUSH_VERSION -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.NODE_PUSH);
                deployApp(applicationCall, requestPacket);
            }

            case TYPE_DEPLOY_SET_VERSION -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.NODE_SET);
                setTriggerApp(applicationCall, requestPacket);
            }

            case TYPE_DEPLOY_DEL_VERSION -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.NODE_REMOVE);
                removeApp(applicationCall, requestPacket);
            }
        }
    }

    private void deployApp(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws InvalidProtocolBufferException {
        Protocol.WorkerInfo workerInfo = requestPacket.getWorker(0);
        Protocol.ServerPacket.Builder serverPacket = Protocol.ServerPacket.newBuilder();

        if(requestPacket.getAppInfoFetchCount() < 1) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Application to deploy not specified"));
            return;
        }

        App.AppInfoFetch appInfoFetch = requestPacket.getAppInfoFetch(0);
        AppPersists appPersists = Helpers.getAppPersists();

        if (!appPersists.hasApp(appInfoFetch.getApp().getAppName())) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application deploy target %s is not available on server", appInfoFetch.getApp().getAppName())));
            return;
        }

        if (!appPersists.hasVersion(appInfoFetch.getApp(), appInfoFetch.getAppVersion(0))) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application %s deploy target version %s is not available on server",
                    appInfoFetch.getApp().getAppName(), appInfoFetch.getAppVersion(0).getVersionName())));
            return;
        }

        serverPacket.setPacketInfo(PacketWrapper.createNewPacket(
                Protocol.ProtocolProcedure.PROCEDURE_REQUEST_TASK,
                Protocol.ActionProcedure.PROCEDURE_ADD_APP_VERSION,
                PersistsPkg.getPackageTokenId(PersistsPkg.getCpuArchByWorker(workerInfo), appInfoFetch)
        ));

        serverPacket.addAppInfoFetch(
                App.AppInfoFetch.newBuilder()
                        .setApp(appPersists.getApp(appInfoFetch.getApp().getAppName()))
                        .addAppVersion(appInfoFetch.getAppVersion(0))
                        .build());

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
