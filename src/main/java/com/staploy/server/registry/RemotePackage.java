package com.staploy.server.registry;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.App;
import com.staploy.server.admin.pkg.AppPackage;
import com.staploy.server.commons.blobs.FileRouteManager;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.utils.Base64;
import io.lettuce.core.api.sync.RedisCommands;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class RemotePackage {

    private String uploadedBlobId;
    private String distributeBlobId;
    private App.InstalledAppInfo packageInfo;

    public boolean hasOriginalBlob() {
        return uploadedBlobId != null && Helpers.getFileRouteManager().hasBlob(uploadedBlobId);
    }

    public static RemotePackage createNew(String blobId) {
        RemotePackage remotePackage = new RemotePackage();
        remotePackage.uploadedBlobId = blobId;
        return remotePackage;
    }

    public static RemotePackage fromAppInfo(App.AppInfo appInfo, App.Version version) throws InvalidProtocolBufferException {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        RemotePackage remotePackage = new RemotePackage();

        if(!isAppExists(appInfo)) {
            throw new IllegalStateException("Target app not exists");
        }

        if(!redisCommands.hexists(String.format(RegistryConst.SCHEME_REGISTRY_APP_METADATA, appInfo.getAppName()), version.getVersionName())) {
            return remotePackage;
        }

        remotePackage.packageInfo = App.InstalledAppInfo.newBuilder()
                .setApp(App.AppInfo.parseFrom(Base64.decode(redisCommands.hget(RegistryConst.SCHEME_REGISTRY_APP_LIST, appInfo.getAppName()))))
                .setCurrentVersion(App.Version.parseFrom(Base64.decode(redisCommands.hget(String.format(RegistryConst.SCHEME_REGISTRY_APP_METADATA, appInfo.getAppName()), version.getVersionName()))))
                .build();

        String tokenStr = redisCommands.hget(remotePackage.getPerAppScheme(), String.format(RegistryConst.SCHEME_REGISTRY_APP_TOKEN, version.getVersionName()));
        remotePackage.distributeBlobId = tokenStr.replace(RegistryConst.SCHEME_REGISTRY_APP_TOKEN_HEADER, "");

        return remotePackage;
    }

    @Nullable
    public static App.InstalledAppInfo removeFromAppInfo(App.AppInfoFetch appInfoFetch) throws InvalidProtocolBufferException {
        if (!appInfoFetch.hasApp()) {
            throw new IllegalArgumentException("Target app to remove not specified");
        }

        if(!isAppExists(appInfoFetch.getApp())) {
            return null;
        }

        ArrayList<App.Version> removedVersions = new ArrayList<>();
        if(appInfoFetch.getAppVersionCount() > 0) for(App.Version version : appInfoFetch.getAppVersionList()) {
            RemotePackage remotePackage = fromAppInfo(appInfoFetch.getApp(), version);
            if(remotePackage.isParsed()) {
                remotePackage.removePackage();
                removedVersions.add(version);
            }
        } else {
            for(App.Version version : getVersions(appInfoFetch.getApp())) {
                RemotePackage remotePackage = fromAppInfo(appInfoFetch.getApp(), version);
                if(remotePackage.isParsed()) {
                    remotePackage.removePackage();
                    removedVersions.add(version);
                }
            }
        }

        return App.InstalledAppInfo.newBuilder()
                .setApp(appInfoFetch.getApp())
                .addAllAvailableVersion(removedVersions)
                .build();
    }

    public static @NotNull List<App.InstalledAppInfo> queryFromAppInfo(App.AppInfoFetch appInfoFetch) throws InvalidProtocolBufferException {
        ArrayList<App.InstalledAppInfo> installedAppInfos = new ArrayList<>();
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();

        if(!appInfoFetch.hasApp()) {
            List<String> apps = redisCommands.hkeys(RegistryConst.SCHEME_REGISTRY_APP_LIST);
            for(String appNames : apps) {
                App.AppInfo appInfo = App.AppInfo.parseFrom(Base64.decode(redisCommands.hget(RegistryConst.SCHEME_REGISTRY_APP_LIST, appNames)));
                installedAppInfos.add(App.InstalledAppInfo.newBuilder()
                        .setApp(appInfo)
                        .addAllAvailableVersion(getVersions(appInfo))
                        .build());
            }
        } else if(appInfoFetch.getAppVersionCount() > 0) {
            App.AppInfo appInfo = App.AppInfo.parseFrom(Base64.decode(redisCommands.hget(RegistryConst.SCHEME_REGISTRY_APP_LIST, appInfoFetch.getApp().getAppName())));
            installedAppInfos.add(App.InstalledAppInfo.newBuilder()
                    .setApp(appInfo)
                    .addAllAvailableVersion(getVersions(appInfoFetch.getApp()).stream().filter(version -> {
                        for(App.Version match : appInfoFetch.getAppVersionList()) {
                            if(match.getVersionName().equals(version.getVersionName())) {
                                return true;
                            }
                        }
                        return false;
                    }).toList())
                    .build());
        } else {
            String rawData = redisCommands.hget(RegistryConst.SCHEME_REGISTRY_APP_LIST, appInfoFetch.getApp().getAppName());
            if(rawData != null && !rawData.isEmpty()) {
                App.AppInfo appInfo = App.AppInfo.parseFrom(Base64.decode(rawData));
                installedAppInfos.add(App.InstalledAppInfo.newBuilder()
                        .setApp(appInfo)
                        .addAllAvailableVersion(getVersions(appInfoFetch.getApp()))
                        .build());
            }
        }
        return installedAppInfos;
    }

    public static List<App.Version> getVersions(App.AppInfo appInfo) throws InvalidProtocolBufferException {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        final String appDataScheme = String.format(RegistryConst.SCHEME_REGISTRY_APP_METADATA, appInfo.getAppName());
        ArrayList<App.Version> versionArrayList = new ArrayList<>();

        for(String key : redisCommands.hkeys(appDataScheme)) {
            if(key.startsWith(RegistryConst.SCHEME_REGISTRY_APP_TOKEN_HEADER)) {
                continue;
            }

            App.Version version = App.Version.parseFrom(Base64.decode(redisCommands.hget(appDataScheme, key)));
            versionArrayList.add(version);
        }
        return versionArrayList;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAppExists(App.AppInfo appInfo) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        return redisCommands.hexists(RegistryConst.SCHEME_REGISTRY_APP_LIST, appInfo.getAppName());
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isParsed() {
        return distributeBlobId != null && !distributeBlobId.isEmpty();
    }

    public void parseUploaded() throws Exception {
        FileRouteManager fileManager = Helpers.getFileRouteManager();
        File originalFile =  fileManager.getBlobFile(uploadedBlobId);

        AppPackage appPackage = AppPackage.createParser(originalFile);
        appPackage.parse();

        packageInfo = App.InstalledAppInfo.newBuilder()
                .setApp(appPackage.getAppInfo())
                .setCurrentVersion(App.Version.newBuilder(appPackage.getBaseVersionInfo())
                        .clearSupportedArch()
                        .addAllSupportedArch(appPackage.getAvailableArch())
                        .build())
                .build();

        File newFile  = new File(Service.getInstance().getArgument().baseDir, String.format(RegistryConst.PACKAGE_PATH, packageInfo.getApp().getAppName(), packageInfo.getApp().getAppName(), packageInfo.getCurrentVersion().getVersionName()));
        boolean _ = newFile.getParentFile().mkdirs();
        Files.move(originalFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        distributeBlobId = fileManager.registerActualFile(newFile, false);
        fileManager.removeBlobDbOnly(uploadedBlobId);

        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        redisCommands.hset(RegistryConst.SCHEME_REGISTRY_APP_LIST, packageInfo.getApp().getAppName(), Base64.encode(packageInfo.getApp().toByteArray()));

        final String perAppScheme = getPerAppScheme();
        redisCommands.hset(perAppScheme, packageInfo.getCurrentVersion().getVersionName(), Base64.encode(packageInfo.getCurrentVersion().toByteArray()));
        redisCommands.hset(perAppScheme, String.format(RegistryConst.SCHEME_REGISTRY_APP_TOKEN, packageInfo.getCurrentVersion().getVersionName()), distributeBlobId);
    }

    public void removePackage() {
        if(!isParsed()) {
            throw new IllegalStateException("Package not parsed!");
        }

        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        redisCommands.hdel(getPerAppScheme(), getPackageInfo().getCurrentVersion().getVersionName());
        redisCommands.hdel(getPerAppScheme(), String.format(RegistryConst.SCHEME_REGISTRY_APP_TOKEN, packageInfo.getCurrentVersion().getVersionName()));
        Helpers.getFileRouteManager().removeBlob(getDistributeBlobId());

        if(redisCommands.hlen(getPerAppScheme()) < 1) {
            redisCommands.hdel(RegistryConst.SCHEME_REGISTRY_APP_LIST, packageInfo.getApp().getAppName());
        }
    }

    public void cleanUpUploaded() {
        if(hasOriginalBlob()) {
            Helpers.getFileRouteManager().removeBlob(uploadedBlobId);
        }
    }

    private String getPerAppScheme() {
        return String.format(RegistryConst.SCHEME_REGISTRY_APP_METADATA, packageInfo.getApp().getAppName());
    }

    public String getDistributeBlobId() {
        return distributeBlobId;
    }

    public App.InstalledAppInfo getPackageInfo() {
        return packageInfo;
    }
}
