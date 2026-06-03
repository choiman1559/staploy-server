package com.staploy.server.worker;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Protocol;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.utils.Base64;
import com.staploy.server.commons.utils.PersistsHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class WorkerPersists {

    private final String workerUUID;
    private final PersistsHelper persistsHelper;

    public WorkerPersists(String workerUUID) {
        this.workerUUID = workerUUID;
        this.persistsHelper = Helpers.getPersistsHelper();
    }

    public static Set<String> getAllStoredUUID() {
        return Helpers.getPersistsHelper().getRedisCommands().hgetall(WorkerConst.SCHEMA_WORKER_INFO).keySet();
    }

    public boolean hasWorkerInfo() {
        try {
            return getWorkerInfo() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public Protocol.WorkerInfo getWorkerInfo() throws InvalidProtocolBufferException {
        String data = persistsHelper.getRedisCommands().hget(WorkerConst.SCHEMA_WORKER_INFO, workerUUID);
        if(data == null || data.isEmpty()) return null;
        else return Protocol.WorkerInfo.parseFrom(Base64.decode(data));
    }

    @Nullable
    public String getWorkerName() throws InvalidProtocolBufferException {
        String data = persistsHelper.getRedisCommands().hget(WorkerConst.SCHEMA_WORKER_NAME, workerUUID);
        if (data == null || data.isBlank()) {
            Protocol.WorkerInfo fetchName = getWorkerInfo();
            if(fetchName == null || fetchName.getWorkerName().isBlank()) {
                return null;
            }

            data = fetchName.getWorkerName();
            persistsHelper.getRedisCommands().hsetnx(WorkerConst.SCHEMA_WORKER_NAME, workerUUID, data);
        }
        return data;
    }

    public void updateWorkerInfo(Protocol.WorkerInfo workerInfo) {
        persistsHelper.getRedisCommands().hsetnx(WorkerConst.SCHEMA_WORKER_INFO, workerUUID,
                Base64.encode(workerInfo.toBuilder().clearInstalledApp().build().toByteArray()));
        persistsHelper.getRedisCommands().hsetnx(WorkerConst.SCHEMA_WORKER_NAME, workerUUID, workerInfo.getWorkerName());
    }
}
