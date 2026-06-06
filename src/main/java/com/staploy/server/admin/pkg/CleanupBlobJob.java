package com.staploy.server.admin.pkg;

import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.commons.utils.Log;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupBlobJob implements InitHelperModule {

    @Override
    public void onServiceAttache() {
        cleanUp();
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            Runnable task = this::cleanUp;
            scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.HOURS);
        }
    }

    @Override
    public void onServiceDetache() {

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

        Log.print("CleanBlobJob", String.format("Cleaned-up unused %d blob(s)!", blobList.size()));
        for(String remainBlob : blobList.keySet()) {
            Helpers.getFileRouteManager().removeBlob(remainBlob);
        }
    }
}
