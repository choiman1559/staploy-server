package com.staploy.server.admin.pkg;

import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.commons.utils.Log;
import com.staploy.server.registry.RegistryConst;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupBlobJob implements InitHelperModule {

    private ScheduledExecutorService scheduler;

    @Override
    public void onServiceAttache() {
        cleanUp();
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::cleanUp, 1, 1, TimeUnit.HOURS);
    }

    @Override
    public void onServiceDetache() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
    }

    public void cleanUp() {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        Map<String, String> blobList = redisCommands.hgetall(ServiceConsts.SCHEMA_BLOB_LIST);

        for(String key : redisCommands.keys("PKG=*")) {
            Map<String, String> pkgMap = redisCommands.hgetall(key);
            for(String archData : pkgMap.keySet()) {
                if(archData.endsWith("-token")) {
                    blobList.remove(pkgMap.get(archData));
                }
            }
        }

        for(String key : redisCommands.keys("REG=*")) {
            Map<String, String> pkgMap = redisCommands.hgetall(key);
            for(String archData : pkgMap.keySet()) {
                if(archData.startsWith(RegistryConst.SCHEME_REGISTRY_APP_TOKEN_HEADER)) {
                    blobList.remove(pkgMap.get(archData));
                }
            }
        }

        Log.print("CleanBlobJob", String.format("Cleaned-up %d unused blob(s)!", blobList.size()));
        for(String remainBlob : blobList.keySet()) {
            Helpers.getFileRouteManager().removeBlob(remainBlob);
        }
    }
}
