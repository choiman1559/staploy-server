package com.staploy.server.commons.utils;

import com.staploy.server.commons.service.Argument;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.service.Service;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class PersistsHelper implements InitHelperModule {

    private static PersistsHelper persistsHelper;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private RedisCommands<String, String> redisCommands;

    public PersistsHelper() {
        // Default constructor for classloader
    }

    public static PersistsHelper getInstance() {
        if(persistsHelper == null) persistsHelper = new PersistsHelper();
        return persistsHelper;
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public StatefulRedisConnection<String, String> getRedisConnection() {
        return redisConnection;
    }

    public RedisCommands<String, String> getRedisCommands() {
        return redisCommands;
    }

    @Override
    public void onServiceAttache() {
        PersistsHelper persistsHelper = getInstance();
        Argument argument = Service.getInstance().getArgument();
        persistsHelper.redisClient = RedisClient.create();
        persistsHelper.redisConnection = persistsHelper.redisClient.connect(RedisURI.builder()
                        .withHost(argument.redisAddress)
                        .withPort(argument.port)
                        .withPassword(argument.redisPassword)
                        .withSsl(argument.redisUseSSL)
                .build());
        persistsHelper.redisCommands = persistsHelper.redisConnection.sync();
    }

    @Override
    public void onServiceDetache() {
        getInstance().redisConnection.close();
        getInstance().redisClient.shutdown();
    }
}
