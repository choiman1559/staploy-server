package com.staploy.server.admin.pkg;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.App;
import com.staploy.Protocol;
import com.staploy.server.admin.AdminConst;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.utils.Base64;
import com.staploy.server.worker.WorkerPersists;
import io.lettuce.core.api.sync.RedisCommands;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class PersistsPkg {

    File baseOutputDir;
    App.AppInfo appInfo;
    App.Version baseVersion;

    public static PersistsPkg create(App.AppInfo appInfo, App.Version baseVersion) {
        PersistsPkg persistsPkg = new PersistsPkg();
        persistsPkg.appInfo = appInfo;
        persistsPkg.baseVersion = baseVersion;

        persistsPkg.baseOutputDir = new File(Service.getInstance().getArgument().baseDir, String.format("%s/%s/%s",
                AdminConst.APP_PATH, appInfo.getAppName(), baseVersion.getVersionName()));
        return persistsPkg;
    }

    @Nullable
    public static String getPackageTokenId(Protocol.CpuArch cpuArch, App.AppInfoFetch appInfoFetch) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        return redisCommands.hget(
                String.format(AdminConst.SCHEMA_PACKAGE_META, appInfoFetch.getApp().getAppName(), appInfoFetch.getAppVersion(0).getVersionName()),
                String.format(AdminConst.SCHEMA_PACKAGE_BLOB_TOKEN, cpuArch)
        );
    }

    @Nullable
    public static App.Version getPackageVersion(Protocol.CpuArch cpuArch, App.AppInfoFetch appInfoFetch) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        try {
            return App.Version.parseFrom(Base64.decode(redisCommands.hget(
                    String.format(AdminConst.SCHEMA_PACKAGE_META, appInfoFetch.getApp().getAppName(), appInfoFetch.getAppVersion(0).getVersionName()),
                    cpuArch.toString()
            )));
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    public void registerPackageBlob(Map<Protocol.CpuArch, AppPackage.ArchPackageBundle> cpuArchBundles) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        Map<String, String> packageData = new HashMap<>();

        for(Protocol.CpuArch cpuArch : cpuArchBundles.keySet()) {
            AppPackage.ArchPackageBundle archPackageBundle = cpuArchBundles.get(cpuArch);
            String token = Helpers.getFileRouteManager().registerActualFile(archPackageBundle.getOutput(appInfo.getAppName(), baseOutputDir), false);

            packageData.put(cpuArch.toString(), Base64.encode(archPackageBundle.getByArchVersionInfo().toByteArray()));
            packageData.put(String.format(AdminConst.SCHEMA_PACKAGE_BLOB_TOKEN, cpuArch), token);
        }
        redisCommands.hset(getPkgMetadataScheme(), packageData);
    }

    public void removePackageBlob() {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        Map<String, String> packageData = redisCommands.hgetall(getPkgMetadataScheme());
        for(String key : packageData.keySet()) {
            if(key.endsWith("-token")) {
                Helpers.getFileRouteManager().removeBlob(packageData.get(key));
            }
        }
        redisCommands.del(getPkgMetadataScheme());
    }

    public boolean hasPackage() {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        return !redisCommands.hgetall(getPkgMetadataScheme()).isEmpty();
    }

    private String getPkgMetadataScheme() {
        return String.format(AdminConst.SCHEMA_PACKAGE_META, appInfo.getAppName(), baseVersion.getVersionName());
    }

    public static Protocol.CpuArch getCpuArchByWorker(Protocol.WorkerInfo workerInfo) {
        try {
            Protocol.WorkerInfo fromDbInfo = new WorkerPersists(workerInfo.getWorkerId()).getWorkerInfo();
            if(fromDbInfo != null) {
                return fromDbInfo.getCpuArch();
            }
        } catch (Exception e) {
            return Protocol.CpuArch.UNKNOWN;
        }
        return Protocol.CpuArch.UNKNOWN;
    }
}
