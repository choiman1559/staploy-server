package com.staploy.server.admin.pkg;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.App;
import com.staploy.Cpus;
import com.staploy.server.admin.AdminConst;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.utils.Base64;
import io.lettuce.core.api.sync.RedisCommands;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AppPersists implements InitHelperModule {

    private RedisCommands<String, String> getPersistCommand() {
        return Helpers.getPersistsHelper().getRedisCommands();
    }

    private String getVersionScheme(App.AppInfo appInfo) {
        return String.format(AdminConst.SCHEMA_APP_VERSIONS, appInfo.getAppName());
    }

    public List<App.AppInfo> getAllApps() throws InvalidProtocolBufferException {
        Map<String, String> allLists = getPersistCommand().hgetall(AdminConst.SCHEMA_APP_LIST);
        ArrayList<App.AppInfo> appInfoArrayList = new ArrayList<>();

        for (String key : allLists.keySet()) {
            appInfoArrayList.add(App.AppInfo.parseFrom(Base64.decode(allLists.get(key))));
        }
        return appInfoArrayList;
    }

    @Nullable
    public App.AppInfo getApp(String appName) throws InvalidProtocolBufferException {
        String rawData = getPersistCommand().hget(AdminConst.SCHEMA_APP_LIST, appName);
        if (rawData == null || rawData.isEmpty()) {
            return null;
        }
        return App.AppInfo.parseFrom(Base64.decode(rawData));
    }

    public boolean hasApp(String appName) throws InvalidProtocolBufferException {
        return getApp(appName) != null;
    }

    public void updateApp(App.AppInfo appInfo) {
        String data = Base64.encode(appInfo.toByteArray());
        getPersistCommand().hset(AdminConst.SCHEMA_APP_LIST, appInfo.getAppName(), data);
    }

    public void removeApp(App.AppInfo appInfo) throws InvalidProtocolBufferException {
        for (App.Version version : getVersions(appInfo)) {
            removeVersion(appInfo, version);
        }

        getPersistCommand().del(getVersionScheme(appInfo));
        getPersistCommand().hdel(AdminConst.SCHEMA_APP_LIST, appInfo.getAppName());
    }

    public List<App.Version> getVersions(App.AppInfo appInfo) throws InvalidProtocolBufferException {
        ArrayList<App.Version> versionArrayList = new ArrayList<>();
        Map<String, String> versionTags = getPersistCommand().hgetall(getVersionScheme(appInfo));

        for (String versionName : versionTags.keySet()) {
            versionArrayList.add(App.Version.parseFrom(Base64.decode(versionTags.get(versionName))));
        }
        return versionArrayList;
    }

    @Nullable
    public App.Version getVersion(App.AppInfo appInfo, String versionName) throws InvalidProtocolBufferException {
        String rawData = getPersistCommand().hget(getVersionScheme(appInfo), versionName);
        if(rawData == null || rawData.isEmpty()) {
            return null;
        }
        return App.Version.parseFrom(Base64.decode(rawData));
    }

    public List<App.Version> getArchSupportVersion(App.AppInfo appInfo, Cpus.CpuArch cpuArch) throws InvalidProtocolBufferException {
        List<App.Version> versionListMap = getVersions(appInfo);
        ArrayList<App.Version> versionArrayList = new ArrayList<>();

        for (App.Version version : versionListMap) {
            if (version.getSupportedArchList().contains(cpuArch)) {
                versionArrayList.add(version);
            }
        }
        return versionArrayList;
    }

    public boolean hasVersion(App.AppInfo appInfo, App.Version version) throws InvalidProtocolBufferException {
        return getVersion(appInfo, version.getVersionName()) != null;
    }

    public void updateVersion(App.AppInfo appInfo, App.Version version, Set<Cpus.CpuArch> cpuArches) {
        App.Version.Builder versionBuilder = App.Version.newBuilder(version);
        versionBuilder.addAllSupportedArch(cpuArches);
        getPersistCommand().hset(getVersionScheme(appInfo), version.getVersionName(), Base64.encode(versionBuilder.build().toByteArray()));
    }

    public void removeVersion(App.AppInfo appInfo, App.Version version) {
        getPersistCommand().hdel(getVersionScheme(appInfo), version.getVersionName());
    }

    public void removeVersionBlob(App.AppInfo appInfo, App.Version version) {
        PersistsPkg persistsPkg = PersistsPkg.create(appInfo, version);
        if (persistsPkg.hasPackage()) {
            persistsPkg.removePackageBlob();
        }
        removeVersion(appInfo, version);
    }

    @Override
    public void onServiceAttache() {

    }

    @Override
    public void onServiceDetache() {

    }
}
