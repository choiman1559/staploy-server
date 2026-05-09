package com.staploy.server.worker;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Protocol;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.utils.Base64;
import com.staploy.server.commons.utils.PersistsHelper;
import org.jetbrains.annotations.Nullable;

public class WorkerPersists {

    private final String workerUUID;
    private final PersistsHelper persistsHelper;

    public WorkerPersists(String workerUUID) {
        this.workerUUID = workerUUID;
        this.persistsHelper = Helpers.getPersistsHelper();
    }

    @Nullable
    public Protocol.WorkerInfo getWorkerInfo() throws InvalidProtocolBufferException {
        String data = persistsHelper.getRedisCommands().hget(WorkerConst.SCHEMA_WORKER_INFO, workerUUID);
        if(data == null || data.isEmpty()) return null;
        else return Protocol.WorkerInfo.parseFrom(Base64.decode(data));
    }

    public void updateWorkerInfo(Protocol.WorkerInfo workerInfo) {
        persistsHelper.getRedisCommands().hsetnx(WorkerConst.SCHEMA_WORKER_INFO, workerUUID,
                Base64.encode(workerInfo.toBuilder().clearInstalledApp().build().toByteArray()));
    }
}
