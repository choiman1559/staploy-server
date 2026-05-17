package com.staploy.server.admin.tasks;

import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.pkg.AppPackage;
import com.staploy.server.admin.pkg.PersistsPkg;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class AppsTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        switch (requestPacket.getAppsTaskType()) {
            case TYPE_APP_NONE -> {

            }

            case TYPE_APP_REGISTER -> {

            }

            case TYPE_APP_LISTS -> {

            }

            case TYPE_APP_DELETE -> {

            }

            case TYPE_APP_BLOB -> {
                try {
                    handleBlobProcess(applicationCall, requestPacket);
                } catch (Exception e) {
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, e.getMessage()));
                }
            }
        }
    }

    private void handleBlobProcess(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws IOException {
        String blobToken = requestPacket.getExtraData();
        AppPackage appPackage = AppPackage.createParser(Helpers.getFileRouteManager().getBlobFile(blobToken));
        appPackage.parse();
        appPackage.buildByArchPackage();

        HashMap<Protocol.CpuArch, AppPackage.ArchPackageBundle> cpuArchBundles = appPackage.getOutputArchives();
        if (!cpuArchBundles.isEmpty()) {
            ArrayList<Protocol.WorkerPacket> workerPackets = new ArrayList<>();
            for(Protocol.CpuArch cpuArch : cpuArchBundles.keySet()) {
                if(cpuArch.equals(Protocol.CpuArch.UNKNOWN)) continue;
                workerPackets.add(Protocol.WorkerPacket.newBuilder()
                        .setWorkerInfo(Protocol.WorkerInfo.newBuilder()
                                .setCpuArch(cpuArch)
                                .addInstalledApp(
                                        App.InstalledAppInfo.newBuilder()
                                                .setApp(appPackage.getAppInfo())
                                                .setCurrentVersion(cpuArchBundles.get(cpuArch).getByArchVersionInfo())
                        .build()).build()).build());
            }

            if (appPackage.getAppInfo() != null && appPackage.getBaseVersionInfo() != null) {
                cpuArchBundles.remove(Protocol.CpuArch.UNKNOWN);
                PersistsPkg.create(appPackage.getAppInfo(), appPackage.getBaseVersionInfo()).registerPackageBlob(cpuArchBundles);
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK, workerPackets.toArray(new Protocol.WorkerPacket[]{})));
            } else {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Cannot parse package metadata"));
            }
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(ServiceConsts.STATUS_ERROR, "Given package not including any CPU arch types"));
        }
    }
}
