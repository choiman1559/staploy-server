package com.staploy.server.commons.utils;

import com.staploy.server.commons.service.Argument;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.service.Service;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class PersistsHelper implements InitHelperModule {

    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final RedisCommands<String, String> redisCommands;

    public PersistsHelper() {
        Argument argument = Service.getInstance().getArgument();
        redisClient = RedisClient.create();
        redisConnection = redisClient.connect(RedisURI.builder()
                .withHost(argument.redisAddress)
                .withPort(argument.redisPort)
                .withPassword(argument.redisPassword)
                .withSsl(argument.redisUseSSL)
                .build());
        redisCommands = redisConnection.sync();
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

    }

    @Override
    public void onServiceDetache() {
        redisConnection.close();
        redisClient.shutdown();
    }
}
