package com.staploy.server.admin.tasks;

import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Registry;
import com.staploy.Users;
import com.staploy.server.admin.Task;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import com.staploy.server.registry.RepoHandler;
import io.ktor.server.application.ApplicationCall;

import java.util.ArrayList;
import java.util.List;

public class RegistryTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        Registry.RegistryRequestPacket registryPacket = requestPacket.getRegistryTaskType();

        switch (registryPacket.getTaskType()) {
            case LOCAL_PULL_PACKAGE -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PULL);
                handlePullPackage(applicationCall, registryPacket);
            }

            case LOCAL_PACKAGE_QUERY -> {
                Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.REGISTRY_PULL);
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
                handleQueryPackage(applicationCall, registryPacket);
            }

            case LOCAL_PACKAGE_CACHE_UPDATE -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PULL);
                handleUpdateCache(applicationCall, registryPacket);
            }

            case LOCAL_LIST_REPOSITORY -> {
                Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.REGISTRY_PULL);
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
                handleListRepo(applicationCall);
            }

            case LOCAL_ADD_REPOSITORY -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PUSH);
                handleAddRepo(applicationCall, registryPacket);
            }

            case LOCAL_REMOVE_REPOSITORY -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_DELETE);
                handleRemoveRepo(applicationCall, registryPacket);
            }

            case LOCAL_MANAGE_TOKEN_REPOSITORY -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.REGISTRY_PUSH);
                handleManageTokenRepo(applicationCall, registryPacket);
            }
        }
    }

    private void handlePullPackage(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        // TODO: implement pull package from remote server
        throw new RuntimeException("Stub!");
    }

    private void handleQueryPackage(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        List<String> repositoryTarget;
        if (registryRequestPacket.getRepositoryUrlCount() > 0) {
            repositoryTarget = registryRequestPacket.getRepositoryUrlList();
        } else {
            repositoryTarget = RepoHandler.getAllRepositories();
        }

        if (repositoryTarget == null) {
            repositoryTarget = new ArrayList<>();
        }

        ArrayList<App.InstalledAppInfo> computedAppInfo = new ArrayList<>();
        ArrayList<String> computedRepoUrl = new ArrayList<>();

        for (String repository : repositoryTarget) {
            RepoHandler repoHandler = RepoHandler.fromUrl(repository);
            repoHandler.queryFromDb(true, true);

            if (repoHandler.getPackageCache() != null) {
                for (App.InstalledAppInfo appInfo : repoHandler.getPackageCache().getAppInfoList()) {
                    if (registryRequestPacket.hasAppInfo()) {
                        if (registryRequestPacket.getAppInfo().getApp().getAppName().equals(appInfo.getApp().getAppName())) {
                            computedAppInfo.add(appInfo);
                            computedRepoUrl.add(repository);
                            break;
                        }
                        continue;
                    }

                    computedAppInfo.add(appInfo);
                    computedRepoUrl.add(repository);
                }
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Registry.RegistryResponsePacket.newBuilder()
                        .addAllAppInfo(computedAppInfo)
                        .addAllRepositoryUrl(computedRepoUrl).build()));
    }

    private void handleUpdateCache(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        ArrayList<String> createdRepositories = new ArrayList<>();
        List<String> listsToUpdate = RepoHandler.getAllRepositories();
        if (listsToUpdate == null || listsToUpdate.isEmpty()) {
            listsToUpdate = new ArrayList<>();
        }

        for (String repoUrl : registryRequestPacket.getRepositoryUrlCount() > 0 ? registryRequestPacket.getRepositoryUrlList() : listsToUpdate) {
            RepoHandler repoHandler = RepoHandler.fromUrl(repoUrl);
            try {
                repoHandler.requestPackageCache();
                createdRepositories.add(String.format("%s$%s", repoUrl, ServiceConsts.STATUS_OK));
            } catch (Exception e) {
                createdRepositories.add(String.format("%s$%s=%s", repoUrl, ServiceConsts.STATUS_ERROR, e.getMessage()));
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, Registry.RegistryResponsePacket.newBuilder()
                .addAllRepositoryUrl(createdRepositories).build()));
    }

    private void handleListRepo(ApplicationCall applicationCall) throws Exception {
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, Registry.RegistryResponsePacket.newBuilder()
                .addAllRepositoryUrl(RepoHandler.getAllRepositories()).build()));
    }

    private void handleAddRepo(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        if (registryRequestPacket.getRepositoryUrlCount() < 1) {
            throw new IllegalArgumentException("List of repository to add is empty");
        }

        ArrayList<String> createdRepositories = new ArrayList<>();
        for (String repoUrl : registryRequestPacket.getRepositoryUrlList()) {
            RepoHandler repoHandler = RepoHandler.fromUrl(repoUrl);
            if (repoHandler.createNewRepository()) {
                createdRepositories.add(repoUrl);
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, Registry.RegistryResponsePacket.newBuilder()
                .addAllRepositoryUrl(createdRepositories).build()));
    }

    private void handleRemoveRepo(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        if (registryRequestPacket.getRepositoryUrlCount() < 1) {
            throw new IllegalArgumentException("List of repository to remove is empty");
        }

        ArrayList<String> createdRepositories = new ArrayList<>();
        for (String repoUrl : registryRequestPacket.getRepositoryUrlList()) {
            RepoHandler repoHandler = RepoHandler.fromUrl(repoUrl);
            if (repoHandler.removeRepository()) {
                createdRepositories.add(repoUrl);
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, Registry.RegistryResponsePacket.newBuilder()
                .addAllRepositoryUrl(createdRepositories).build()));
    }

    private void handleManageTokenRepo(ApplicationCall applicationCall, Registry.RegistryRequestPacket registryRequestPacket) throws Exception {
        if (registryRequestPacket.getRepositoryUrlCount() != 1) {
            throw new IllegalArgumentException("Repository argument invalid for token management");
        }

        RepoHandler repoHandler = RepoHandler.fromUrl(registryRequestPacket.getRepositoryUrl(0));
        repoHandler.setAuthToken(registryRequestPacket.getBlobId());
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK));
    }
}
