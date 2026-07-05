package com.staploy.server.admin.tasks;

import com.auth0.jwt.JWT;
import com.staploy.Admin;
import com.staploy.Users;
import com.staploy.server.admin.AdminConst;
import com.staploy.server.admin.Task;
import com.staploy.server.admin.UserPersistent;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import com.staploy.server.commons.utils.Base64;
import com.staploy.server.packet.PacketWrapper;
import io.ktor.server.application.ApplicationCall;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;

public class UserTask extends Task {
    @Override
    public void performTask(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext userContext) throws Exception {
        Users.UserRequestPacket userRequestPacket = requestPacket.getUserTaskType();

        switch (userRequestPacket.getUserTaskTypes()) {
            case TYPE_USER_CREATE -> {
                Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.USER_MANAGE);
                updateUser(applicationCall, requestPacket, userContext);
            }

            case TYPE_USER_REMOVE -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.USER_MANAGE);
                removeUser(applicationCall, requestPacket);
            }

            case TYPE_USER_LOGIN -> loginUser(applicationCall, requestPacket);

            case TYPE_USER_RBAC ->{
                registerManagement(applicationCall, userContext, Users.PermissionFlag.USER_MANAGE);
                updateRBAC(applicationCall, requestPacket);
            }

            case TYPE_USER_AUDIT -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.USER_MANAGE);
                fetchAuditLogs(applicationCall, requestPacket);
            }

            case TYPE_USER_LISTS -> {
                registerManagement(applicationCall, userContext, Users.PermissionFlag.QUERY_ENDPOINT, false);
                fetchUserLists(applicationCall, requestPacket);
            }

            case TYPE_TOKEN_REFRESH -> {
                Helpers.getAuditDispatcher().attachFlags(applicationCall, Users.PermissionFlag.USER_MANAGE);
                refreshTokenVer(applicationCall, requestPacket, userContext);
            }
        }
    }

    private void updateUserVersion(String uuid, boolean removal) throws NullPointerException {
        UserPersistent userPersistent = UserPersistent.fromUuid(uuid);
        Users.UserMetadata.Builder userMetadata = Users.UserMetadata.newBuilder(userPersistent.getMetadata());

        long version = userMetadata.getVersion();
        if (version >= Long.MAX_VALUE - 1) {
            version = 0;
        }
        userMetadata.setVersion(removal ? -1 : version + 1);

        if (removal) {
            userMetadata.setPermissions(Users.PermissionFlag.USERS_NONE_VALUE);
            userMetadata.clearRoleName();
        }
        userPersistent.updateMetadata(userMetadata.build());
    }

    private void fetchAuditLogs(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Users.UserResponsePacket.newBuilder().addAllAuditData(Helpers.getAuditDispatcher().queryLogs()).build()));
    }

    private void refreshTokenVer(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext authContext) throws Exception {
        if (!requestPacket.getUserTaskType().hasUserLoginInfo()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("cannot find user data parameter"));
            return;
        }

        Users.UserLoginInfo userLoginInfo = requestPacket.getUserTaskType().getUserLoginInfo();
        UserPersistent userPersistent = UserPersistent.fromUserName(userLoginInfo.getUserName());

        if(authContext.authValid() && authContext.userMetadata() != null && authContext.userMetadata().getUuid().equals(userPersistent.uuid())) {
            registerManagement(applicationCall, authContext, Users.PermissionFlag.USERS_NONE);
        } else {
            registerManagement(applicationCall, authContext, Users.PermissionFlag.USER_MANAGE);
        }

        if (userLoginInfo.getUserName().isEmpty() || !userPersistent.hasUser()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user name not found"));
            return;
        }

        updateUserVersion(userPersistent.uuid(), false);
        Service.replyPacket(applicationCall, PacketWrapper.makePacket("refreshed token: " + userLoginInfo.getUserName()));
    }

    private void fetchUserLists(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        if (requestPacket.getUserTaskType().hasUserLoginInfo()) {
            String userName = requestPacket.getUserTaskType().getUserLoginInfo().getUserName();
            if(userName.isEmpty()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user name not found"));
            } else {
                UserPersistent userPersistent = UserPersistent.fromUserName(userName);
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                        Users.UserResponsePacket.newBuilder().addUserMetaDatas(userPersistent.getMetadata()).build()));
            }
            return;
        }

        Map<String, String> allUsers = Helpers.getPersistsHelper().getRedisCommands().hgetall(AdminConst.SCHEMA_USER_UUIDS);
        ArrayList<Users.UserMetadata> userMetadataList = new ArrayList<>();

        for(String uuid : allUsers.values()) {
            Users.UserMetadata metadata = UserPersistent.fromUuid(uuid).getMetadata();
            if(metadata != null && metadata.getVersion() >= 0) {
                userMetadataList.add(metadata);
            }
        }

        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Users.UserResponsePacket.newBuilder().addAllUserMetaDatas(userMetadataList).build()));
    }

    private void updateUser(ApplicationCall applicationCall, Admin.RequestPacket requestPacket, AuthContext authContext) throws Exception {
        if (!requestPacket.getUserTaskType().hasUserLoginInfo()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("cannot find user data parameter"));
            return;
        }

        Users.UserLoginInfo userLoginInfo = requestPacket.getUserTaskType().getUserLoginInfo();
        UserPersistent userPersistent = UserPersistent.fromUserName(userLoginInfo.getUserName());

        if (userLoginInfo.getUserName().isEmpty() || !userLoginInfo.hasUserPassword()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user name or password is empty"));
            return;
        }

        if (!userPersistent.hasUser()) {
            registerManagement(applicationCall, authContext, Users.PermissionFlag.USER_MANAGE);
            userPersistent = UserPersistent.newUser(userLoginInfo.getUserName());

            Users.UserMetadata.Builder userBuilder = Users.UserMetadata.newBuilder();
            userBuilder.setUuid(userPersistent.uuid())
                    .setUserName(userLoginInfo.getUserName())
                    .setVersion(0)
                    .setPermissions(Users.PermissionFlag.USERS_NONE_VALUE);

            userPersistent.updateMetadata(userBuilder.build());
            userPersistent.setPassword(Base64.encodeBcrypt(userLoginInfo.getUserPassword().toByteArray()));

            Service.replyPacket(applicationCall, PacketWrapper.makePacket("created user: " + userLoginInfo.getUserName()));
            return;
        } else if (authContext.userMetadata() != null && authContext.userMetadata().getUuid().equals(userPersistent.uuid())){
            registerManagement(applicationCall, authContext, Users.PermissionFlag.USERS_NONE);
        } else {
            registerManagement(applicationCall, authContext, Users.PermissionFlag.USER_MANAGE);
        }

        userPersistent.setPassword(Base64.encodeBcrypt(userLoginInfo.getUserPassword().toByteArray()));
        updateUserVersion(userPersistent.uuid(), false);
        Service.replyPacket(applicationCall, PacketWrapper.makePacket("updated user: " + userLoginInfo.getUserName()));
    }

    private void removeUser(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        RedisCommands<String, String> redisCommands = Helpers.getPersistsHelper().getRedisCommands();
        if (!requestPacket.getUserTaskType().hasUserLoginInfo()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("cannot find user data parameter"));
            return;
        }

        Users.UserLoginInfo userLoginInfo = requestPacket.getUserTaskType().getUserLoginInfo();
        String uuid = redisCommands.hget(AdminConst.SCHEMA_USER_UUIDS, userLoginInfo.getUserName());

        if (uuid == null || uuid.isEmpty() || userLoginInfo.getUserName().isEmpty()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user name is empty or not found"));
            return;
        }

        updateUserVersion(uuid, true);
        redisCommands.hdel(AdminConst.SCHEMA_USER_PASSWD, uuid);
        Service.replyPacket(applicationCall, PacketWrapper.makePacket("removed user: " + userLoginInfo.getUserName()));
    }

    private void loginUser(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        if (!requestPacket.getUserTaskType().hasUserLoginInfo()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("cannot find user data parameter"));
            return;
        }

        Users.UserLoginInfo userLoginInfo = requestPacket.getUserTaskType().getUserLoginInfo();
        Helpers.getAuditDispatcher().attachUserInfo(applicationCall, userLoginInfo);
        UserPersistent userPersistent = UserPersistent.fromUserName(userLoginInfo.getUserName());

        if (userLoginInfo.getUserName().isEmpty() || !userPersistent.hasUser()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user not found"));
            return;
        }

        Users.UserMetadata userMetadata = userPersistent.getMetadata();
        if (userMetadata == null) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user information not found"));
            return;
        }

        if (!userLoginInfo.hasUserPassword() || !Base64.validateBcrypt(userLoginInfo.getUserPassword().toByteArray(), userPersistent.getPassword())) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("login credential invalid"));
            return;
        }

        Instant nowUtc = Instant.now();
        String jwtKey = JWT.create()
                .withAudience(Service.getInstance().getServerUUID())
                .withIssuer(Service.getInstance().getArgument().host)
                .withClaim(ServiceConsts.JWT_CLAIM_USERNAME, userLoginInfo.getUserName())
                .withClaim(ServiceConsts.JWT_CLAIM_VERSION, userMetadata.getVersion())
                .withClaim(ServiceConsts.JWT_CLAIM_PERMISSION, userMetadata.getPermissions())
                .withClaim(ServiceConsts.JWT_CLAIM_UUID, userPersistent.uuid())
                .withExpiresAt(nowUtc.plus(365, ChronoUnit.DAYS))
                .sign(Helpers.getJwtCertManager().getRsaKey());

        Users.UserLoginInfo loginToken = Users.UserLoginInfo.newBuilder().setUserName(userLoginInfo.getUserName()).setUserToken(jwtKey).build();
        Service.replyPacket(applicationCall, PacketWrapper.makePacket(ServiceConsts.STATUS_OK,
                Users.UserResponsePacket.newBuilder().setUserLoginInfo(loginToken).build()));
    }

    private void updateRBAC(ApplicationCall applicationCall, Admin.RequestPacket requestPacket) throws Exception {
        if (!requestPacket.getUserTaskType().hasUserLoginInfo()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("cannot find user data parameter"));
            return;
        }

        Users.UserLoginInfo userLoginInfo = requestPacket.getUserTaskType().getUserLoginInfo();
        UserPersistent userPersistent = UserPersistent.fromUserName(userLoginInfo.getUserName());

        if (userLoginInfo.getUserName().isEmpty() || !userPersistent.hasUser()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("user name not found"));
            return;
        }

        if (!requestPacket.getUserTaskType().hasPermissions()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("no given permission value"));
            return;
        }

        updateUserVersion(userPersistent.uuid(), false);
        Users.UserMetadata.Builder userMetadata = Users.UserMetadata.newBuilder(userPersistent.getMetadata());
        userMetadata.setPermissions(requestPacket.getUserTaskType().getPermissions());

        userPersistent.updateMetadata(userMetadata.build());
        Service.replyPacket(applicationCall, PacketWrapper.makePacket("permitted user: " + userLoginInfo.getUserName() + " as " + requestPacket.getUserTaskType().getPermissions()));
    }
}
