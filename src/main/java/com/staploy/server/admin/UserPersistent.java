package com.staploy.server.admin;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Users;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.utils.Base64;
import io.lettuce.core.api.sync.RedisCommands;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@SuppressWarnings("CallToPrintStackTrace")
public record UserPersistent(String uuid) {

    public static UserPersistent fromUuid(String uuid) {
        return new UserPersistent(uuid);
    }

    public static UserPersistent fromUserName(String userName) {
        String uuid = Helpers.getPersistsHelper().getRedisCommands().hget(AdminConst.SCHEMA_USER_UUIDS, userName);
        return new UserPersistent(uuid);
    }

    public static UserPersistent newUser(String userName) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        final String newUuid = UUID.randomUUID().toString();
        redisCommands.hset(AdminConst.SCHEMA_USER_UUIDS, userName, newUuid);
        return new UserPersistent(newUuid);
    }

    @Nullable
    public Users.UserMetadata getMetadata() {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        String rawData = redisCommands.hget(AdminConst.SCHEMA_USER_METADATA, uuid);

        if(rawData == null || rawData.isEmpty()) {
            return null;
        }

        try {
            return Users.UserMetadata.parseFrom(Base64.decode(rawData));
        } catch (InvalidProtocolBufferException e) {
            if(Service.getInstance().getArgument().isDebug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public void updateMetadata(Users.UserMetadata metadata) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        redisCommands.hset(AdminConst.SCHEMA_USER_METADATA, uuid, Base64.encode(metadata.toByteArray()));
    }

    public String getPassword() {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        return redisCommands.hget(AdminConst.SCHEMA_USER_PASSWD, uuid);
    }

    public void setPassword(String password) {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        redisCommands.hset(AdminConst.SCHEMA_USER_PASSWD, uuid, password);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasUser() {
        if(uuid == null || uuid.isEmpty()) {
            return false;
        }

        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        String rawData = redisCommands.hget(AdminConst.SCHEMA_USER_METADATA, uuid);

        if(rawData == null || rawData.isEmpty()) {
            return false;
        }

        try {
            return Users.UserMetadata.parseFrom(Base64.decode(rawData)).getVersion() >= 0;
        } catch (InvalidProtocolBufferException e) {
            if(Service.getInstance().getArgument().isDebug) {
                e.printStackTrace();
            }
            return false;
        }
    }
}
