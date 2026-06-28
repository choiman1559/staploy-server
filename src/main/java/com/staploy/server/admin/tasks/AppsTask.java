package com.staploy.server.admin.tasks;

import com.staploy.*;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.pkg.AppPackage;
import com.staploy.server.admin.pkg.AppPersists;
import com.staploy.server.admin.pkg.PersistsPkg;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AppsTask extends Task {

    private static final ConcurrentHashMap<String, AppPackage> packageMap = new ConcurrentHashMap<>();

    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        switch (requestPacket.getAppsTaskType()) {
            case TYPE_APP_NONE -> Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, ServiceConsts.ERROR_ILLEGAL_ARGUMENT));

            case TYPE_APP_REGISTER -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.APP_CREATE);
                handleAppCreateProcess(applicationCall, requestPacket);
            }

            case TYPE_APP_LISTS -> handleAppListProcess(applicationCall, requestPacket);

            case TYPE_APP_DELETE -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.APP_DELETE);
                handleAppDeleteProcess(applicationCall, requestPacket);
            }

            case TYPE_APP_PKG_CREATE -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.APP_UPLOAD);
                handleCreatePkgProcess(applicationCall, requestPacket);
            }

            case TYPE_APP_PKG_PARSE -> {
                userContext.matchPermissionThrows(Users.PermissionFlag.APP_UPLOAD);
                handleParsePkgProcess(applicationCall, requestPacket);
            }
        }
    }

    private void handleAppCreateProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        if (requestPacket.getAppInfoFetchCount() < 1) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Application to deploy not specified"));
            return;
        }

        App.AppInfoFetch appInfoFetch = requestPacket.getAppInfoFetch(0);
        AppPersists appPersists = Helpers.getAppPersists();

        if (appPersists.hasApp(appInfoFetch.getApp().getAppName())) {
            appPersists.updateApp(appInfoFetch.getApp());
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(String.format("Updated App information of %s", appInfoFetch.getApp().getAppName())));
        } else {
            appPersists.updateApp(appInfoFetch.getApp());
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(String.format("Created App %s", appInfoFetch.getApp().getAppName())));
        }
    }

    private void handleAppListProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        AppPersists appPersists = Helpers.getAppPersists();
        ArrayList<App.InstalledAppInfo> installedAppInfos = new ArrayList<>();
        if(requestPacket.getAppInfoFetchCount() < 1) {
            for(App.AppInfo appInfo: appPersists.getAllApps()) {
                installedAppInfos.add(App.InstalledAppInfo.newBuilder()
                        .setApp(appInfo)
                        .addAllAvailableVersion(appPersists.getVersions(appInfo))
                        .build());
            }
        } else {
            for(App.AppInfoFetch appInfoFetch : requestPacket.getAppInfoFetchList()) {
                if (!Helpers.getAppPersists().hasApp(appInfoFetch.getApp().getAppName())) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application target %s is not available on server", appInfoFetch.getApp().getAppName())));
                    return;
                }

                installedAppInfos.add(App.InstalledAppInfo.newBuilder()
                                .setApp(appPersists.getApp(appInfoFetch.getApp().getAppName()))
                                .addAllAvailableVersion(appPersists.getVersions(appInfoFetch.getApp()))
                        .build());
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Protocol.WorkerPacket.newBuilder().setWorkerInfo(Protocol.WorkerInfo.newBuilder()
                        .addAllInstalledApp(installedAppInfos)
                        .build()).build()
        ));
    }

    private void handleAppDeleteProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        if(requestPacket.getAppInfoFetchCount() < 1) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("Application to delete not specified"));
            return;
        }

        AppPersists appPersists = Helpers.getAppPersists();
        for(App.AppInfoFetch appInfoFetch : requestPacket.getAppInfoFetchList()) {
            if (!Helpers.getAppPersists().hasApp(appInfoFetch.getApp().getAppName())) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application target %s is not available on server", appInfoFetch.getApp().getAppName())));
                return;
            }

            if(appInfoFetch.getAppVersionCount() > 0) {
                for(App.Version version : appInfoFetch.getAppVersionList()) {
                    appPersists.removeVersionBlob(appInfoFetch.getApp(), version);
                }
                continue;
            }

            for(App.Version version : appPersists.getVersions(appInfoFetch.getApp())) {
                appPersists.removeVersionBlob(appInfoFetch.getApp(), version);
            }
            appPersists.removeApp(appInfoFetch.getApp());
        }
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK));
    }

    private static AppPackage getPackage(String blobToken) throws IOException {
        AppPackage appPackage;
        if (packageMap.containsKey(blobToken)) {
            appPackage = packageMap.get(blobToken);
        } else {
            appPackage = AppPackage.createParser(Helpers.getFileRouteManager().getBlobFile(blobToken));
            packageMap.put(blobToken, appPackage);
        }

        if (appPackage.isNotParsed()) {
            appPackage.parse();
        }

        return appPackage;
    }

    private void handleParsePkgProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        String blobToken = requestPacket.getExtraData();
        AppPackage appPackage = getPackage(blobToken);

        if (!Helpers.getAppPersists().hasApp(appPackage.getAppInfo().getAppName())) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application deploy target %s is not available on server", appPackage.getAppInfo().getAppName())));
            return;
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Protocol.WorkerPacket.newBuilder().setWorkerInfo(
                        Protocol.WorkerInfo.newBuilder()
                                .addInstalledApp(
                                        App.InstalledAppInfo.newBuilder()
                                                .setApp(appPackage.getAppInfo())
                                                .setCurrentVersion(appPackage.getBaseVersionInfo())
                                                .build()).build()).build()));
    }

    private void handleCreatePkgProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        String blobToken = requestPacket.getExtraData();
        AppPackage appPackage = getPackage(blobToken);

        if (!Helpers.getAppPersists().hasApp(appPackage.getAppInfo().getAppName())) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(String.format("Application deploy target %s is not available on server", appPackage.getAppInfo().getAppName())));
            Helpers.getFileRouteManager().removeBlob(blobToken);
            return;
        }

        PersistsPkg persistsPkg = PersistsPkg.create(appPackage.getAppInfo(), appPackage.getBaseVersionInfo());
        if (persistsPkg.hasPackage()) {
            persistsPkg.removePackageBlob();
            Helpers.getAppPersists().removeVersion(appPackage.getAppInfo(), appPackage.getBaseVersionInfo());
        }

        appPackage.buildByArchPackage();
        HashMap<Cpus.CpuArch, AppPackage.ArchPackageBundle> cpuArchBundles = appPackage.getOutputArchives();

        if (!cpuArchBundles.isEmpty()) {
            ArrayList<Protocol.WorkerPacket> workerPackets = new ArrayList<>();
            for (Cpus.CpuArch cpuArch : cpuArchBundles.keySet()) {
                if (cpuArch.equals(Cpus.CpuArch.UNKNOWN)) continue;
                workerPackets.add(Protocol.WorkerPacket.newBuilder()
                        .setWorkerInfo(Protocol.WorkerInfo.newBuilder()
                                .setCpuArch(cpuArch)
                                .addInstalledApp(
                                        App.InstalledAppInfo.newBuilder()
                                                .setApp(appPackage.getAppInfo())
                                                .setCurrentVersion(cpuArchBundles.get(cpuArch).getByArchVersionInfo())
                                                .build()).build()).build());
            }

            //TODO: handles "shareOnly" flags on package
            if(cpuArchBundles.size() == 1 && cpuArchBundles.containsKey(Cpus.CpuArch.UNKNOWN)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Given package not including any CPU arch types without share"));
                Helpers.getFileRouteManager().removeBlob(blobToken);
                return;
            }

            cpuArchBundles.remove(Cpus.CpuArch.UNKNOWN);
            persistsPkg.registerPackageBlob(cpuArchBundles);
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, workerPackets.toArray(new Protocol.WorkerPacket[]{})));

            packageMap.remove(blobToken);
            Helpers.getFileRouteManager().removeBlob(blobToken);
            Helpers.getAppPersists().updateVersion(appPackage.getAppInfo(), appPackage.getBaseVersionInfo(), appPackage.getAvailableArch());
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Given package not including any CPU arch types"));
            Helpers.getFileRouteManager().removeBlob(blobToken);
        }
    }
}
