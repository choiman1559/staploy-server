package com.staploy.server.admin;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.Protocol;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.worker.WorkerPersists;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupPersistent {
    private static final ConcurrentHashMap<String, GroupPersistent> groupPersistentMutex = new ConcurrentHashMap<>();
    private String groupName;

    public static GroupPersistent getInstance(String groupName) {
        if(groupPersistentMutex.isEmpty() || !groupPersistentMutex.containsKey(groupName)) {
            GroupPersistent groupPersistent = new GroupPersistent();
            groupPersistent.groupName = groupName;
            groupPersistentMutex.put(groupName, groupPersistent);
            return groupPersistent;
        }
        return groupPersistentMutex.get(groupName);
    }

    public boolean isGroupForAll() {
        return groupName.equals(AdminConst.PREFIX_GROUP_ALL);
    }

    public void checkAndThrowGroupAll() {
        if(isGroupForAll()) {
            throw new IllegalArgumentException("group action for target `all` is prohibited");
        }
    }

    public String getGroupName() {
        return groupName;
    }

    public static Set<String> getAllGroups() {
        return Helpers.getPersistsHelper().getRedisCommands().smembers(AdminConst.SCHEME_GROUP_LIST);
    }

    public String createGroup() {
        checkAndThrowGroupAll();
        Helpers.getPersistsHelper().getRedisCommands().sadd(AdminConst.SCHEME_GROUP_LIST, groupName);
        return groupName;
    }

    public String deleteGroup() {
        checkAndThrowGroupAll();
        Helpers.getPersistsHelper().getRedisCommands().srem(AdminConst.SCHEME_GROUP_LIST, groupName);
        Helpers.getPersistsHelper().getRedisCommands().del(getPersistentGroupScheme());
        return groupName;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasGroup() {
        return isGroupForAll() ||
                Helpers.getPersistsHelper().getRedisCommands().sismember(AdminConst.SCHEME_GROUP_LIST, groupName);
    }

    public void addWorkers(List<String> workers) {
        checkAndThrowGroupAll();
        Helpers.getPersistsHelper().getRedisCommands().sadd(getPersistentGroupScheme(), getParsedNameOrId(workers).toArray(new String[]{}));
    }

    public void removeWorkers(List<String> workers) {
        checkAndThrowGroupAll();
        Helpers.getPersistsHelper().getRedisCommands().srem(getPersistentGroupScheme(), getParsedNameOrId(workers).toArray(new String[]{}));
    }

    public List<Protocol.WorkerInfo> getWorkerList() {
        if(isGroupForAll()) {
            return Helpers.getWorkerManager().getAllActiveSessions();
        }

        ArrayList<Protocol.WorkerInfo> workerInfos = new ArrayList<>();
        for (String key : Helpers.getPersistsHelper().getRedisCommands().smembers(getPersistentGroupScheme())) {
            try {
                workerInfos.add(Protocol.WorkerInfo.newBuilder()
                        .setWorkerId(key)
                        .setWorkerName(new WorkerPersists(key).getWorkerName())
                        .build());
            } catch (Exception _) {
                //nothing to do; just continue
            }
        }
        return workerInfos;
    }

    private String getPersistentGroupScheme() {
        return String.format(AdminConst.SCHEMA_GROUP, groupName);
    }

    public static List<String> getParsedNameOrId(List<String> rawLists) {
        ArrayList<String> resultList = new ArrayList<>();
        for(String data : rawLists) {
            String result = findWorkerId(data);
            if(!result.isBlank()) {
                resultList.add(result);
            }
        }
        return resultList;
    }

    public static String findWorkerId(String data) {
        if(isUUID(data) && new WorkerPersists(data).hasWorkerInfo()) {
            return data;
        }

        String name = Helpers.getWorkerManager().getWorkerIdByName(data);
        if(name != null && !name.isBlank()) {
            return name;
        }

        for(String allIds: WorkerPersists.getAllStoredUUID()) {
            try {
                if (Objects.equals(data, new WorkerPersists(allIds).getWorkerName())) {
                    return allIds;
                }
            } catch (InvalidProtocolBufferException _) {
                //nothing to do; just continue...
            }
        }
        return "";
    }

    private static boolean isUUID(String data) {
        try {
            return UUID.fromString(data).version() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
