package com.staploy.server.admin.tasks;

import com.staploy.Admin;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.pkg.AppPackage;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;

import java.io.IOException;

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

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Protocol.WorkerPacket.newBuilder().setWorkerInfo(Protocol.WorkerInfo.newBuilder().addInstalledApp(App.InstalledAppInfo.newBuilder()
                .setApp(appPackage.getAppInfo()).setCurrentVersion(appPackage.getBaseVersionInfo())
                .build()).build()).build()));
    }
}
