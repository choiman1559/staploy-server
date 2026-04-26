package com.staploy.server.worker;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.commons.utils.PersistsHelper;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkerPersists {

    private final String workerUUID;
    private final PersistsHelper persistsHelper;

    public WorkerPersists(String workerUUID) {
        this.workerUUID = workerUUID;
        this.persistsHelper = PersistsHelper.getInstance();
    }

    private String getAppHashListKey() {
        return String.format(WorkerConst.SCHEMA_WORKER_APPS, workerUUID);
    }

    @Nullable
    public Protocol.WorkerInfo getWorkerInfo() throws InvalidProtocolBufferException {
        return Protocol.WorkerInfo.parseFrom(
                persistsHelper.getRedisCommands()
                        .hget(WorkerConst.SCHEMA_WORKER_INFO, workerUUID)
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    public App.InstalledAppInfo getInstalledAppInfo(String appName) throws InvalidProtocolBufferException {
        return App.InstalledAppInfo.parseFrom(
                persistsHelper.getRedisCommands().hget(getAppHashListKey(), appName)
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    public Map<String, App.InstalledAppInfo> getAllInstalledAppInfos() throws InvalidProtocolBufferException {
        HashMap<String, App.InstalledAppInfo> appsList = new HashMap<>();
        Map<String, String> rawMap = persistsHelper.getRedisCommands().hgetall(getAppHashListKey());

        for(String key : rawMap.keySet()) {
            appsList.put(key, App.InstalledAppInfo.parseFrom(rawMap.get(key).getBytes(StandardCharsets.UTF_8)));
        }
        return appsList;
    }

    public void updateWorkerInfo(Protocol.WorkerInfo workerInfo) {
        if(workerInfo.getInstalledAppCount() > 0) {
            updateWorkerInstalledAppInfo(workerInfo.getInstalledAppList());
        }
        persistsHelper.getRedisCommands().hsetnx(WorkerConst.SCHEMA_WORKER_INFO, workerUUID,
                new String(workerInfo.toBuilder().clearInstalledApp().build().toByteArray()));
    }

    public void updateInstalledAppInfo(String AppName, App.InstalledAppInfo appInfo) {
        persistsHelper.getRedisCommands().hset(getAppHashListKey(), appInfo.getApp().getAppName(), new String(appInfo.toByteArray()));
    }

    public void updateWorkerInstalledAppInfo(List<App.InstalledAppInfo> installedAppInfo) {
        HashMap<String, String> hashMap = new HashMap<>();
        for(App.InstalledAppInfo appInfo : installedAppInfo) {
            hashMap.put(appInfo.getApp().getAppName(), new String(appInfo.toByteArray()));
        }
        persistsHelper.getRedisCommands().hset(getAppHashListKey(), hashMap);
    }
}
