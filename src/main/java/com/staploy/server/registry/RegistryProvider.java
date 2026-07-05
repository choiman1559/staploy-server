package com.staploy.server.registry;

import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Registry;
import com.staploy.Users;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Argument;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

import java.util.List;
import java.util.Objects;

public class RegistryProvider extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        Registry.RegistryRequestPacket registryPacket = requestPacket.getRegistryTaskType();
        switch (registryPacket.getTaskType()) {
            case TASK_PUSH -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PUSH);
                handlePush(applicationCall, registryPacket);
            }

            case TASK_PULL -> {
                Argument argument = Service.getInstance().getArgument();
                if(userContext.userMetadata() == null && argument.allowPullAnonymous) {
                    Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.REGISTRY_PULL);
                } else if(userContext.userMetadata() != null && !userContext.matchPermission(Users.PermissionFlag.REGISTRY_PULL) && argument.allowPullNonPermit) {
                    Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.REGISTRY_PULL);
                } else if(userContext.userMetadata() != null) {
                    registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PULL);
                } else {
                    throw new SecurityException("User permission for pull package invalid");
                }

                handlePull(applicationCall, registryPacket);
            }

            case TASK_REMOVE -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_DELETE);
                handleRemove(applicationCall, registryPacket);
            }

            case TASK_LIST -> {
                Argument argument = Service.getInstance().getArgument();
                if(userContext.userMetadata() == null && !argument.allowPullAnonymous) {
                    throw new SecurityException("User permission for listing package invalid");
                }

                if(userContext.userMetadata() != null && !argument.allowPullNonPermit) {
                    Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.REGISTRY_PULL);
                    registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
                }
                handleList(applicationCall, registryPacket);
            }

            default -> Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Invalid registry task type"));
        }
    }

    private void handlePush(ApplicationCall applicationCall, Registry.RegistryRequestPacket requestPacket) throws Exception {
        RemotePackage remotePackage = RemotePackage.createNew(requestPacket.getBlobId());
        if(!remotePackage.hasOriginalBlob()) {
            throw new IllegalArgumentException("Given blob id is invalid");
        }

        remotePackage.parseUploaded();
        if(!remotePackage.isParsed()) {
            remotePackage.cleanUpUploaded();
            throw new IllegalStateException("failed to parse uploaded package");
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Registry.RegistryResponsePacket.newBuilder().addAppInfo(remotePackage.getPackageInfo()).build()));
    }

    private void handlePull(ApplicationCall applicationCall, Registry.RegistryRequestPacket requestPacket) throws Exception {
        if(!requestPacket.hasAppInfo() || !requestPacket.getAppInfo().hasApp() || requestPacket.getAppInfo().getAppVersionCount() != 1) {
            throw new IllegalArgumentException("Given package metadata is invalid");
        }

        RemotePackage remotePackage = RemotePackage.fromAppInfo(requestPacket.getAppInfo().getApp(), requestPacket.getAppInfo().getAppVersion(0));
        if(Objects.requireNonNullElse(remotePackage.getDistributeBlobId(), "").isEmpty()) {
            throw new IllegalStateException("No package available");
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Registry.RegistryResponsePacket.newBuilder().setBlobId(remotePackage.getDistributeBlobId()).build()));
    }

    private void handleRemove(ApplicationCall applicationCall, Registry.RegistryRequestPacket requestPacket) throws Exception {
        if(!requestPacket.hasAppInfo() || !requestPacket.getAppInfo().hasApp()) {
            throw new IllegalArgumentException("Given package metadata is invalid");
        }

        App.InstalledAppInfo appInfo = RemotePackage.removeFromAppInfo(requestPacket.getAppInfo());
        if(appInfo == null) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Cannot find target to remove"));
        }
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Registry.RegistryResponsePacket.newBuilder().addAppInfo(appInfo).build()));
    }

    private void handleList(ApplicationCall applicationCall, Registry.RegistryRequestPacket requestPacket) throws Exception {
        List<App.InstalledAppInfo> appInfo = RemotePackage.queryFromAppInfo(requestPacket.getAppInfo());
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Registry.RegistryResponsePacket.newBuilder().addAllAppInfo(appInfo).build()));
    }
}
