package com.staploy.server.worker;

import com.staploy.Protocol;
import com.staploy.server.commons.service.Helpers;
import com.staploy.server.commons.service.InitHelperModule;
import com.staploy.server.commons.utils.WebSocketUtil;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public class WorkerManager implements InitHelperModule {

    private final static String LogTAG = "WorkerManager";
    private final ConcurrentHashMap<String, Protocol.WorkerInfo> activeSessionWorker;

    public static class WorkerSessionInfo {

        private final String workerUUID;
        private final WorkerPersists workerPersists;

        public WorkerSessionInfo(String workerUUID) {
            this.workerUUID = workerUUID;
            this.workerPersists = new WorkerPersists(this.workerUUID);
        }

        public Protocol.WorkerInfo getWorkerInfo() {
            return getWorkerInfo(true);
        }

        @Nullable
        public Protocol.WorkerInfo getWorkerInfo(boolean fromPersists) {
            return getWorkerManager().getWorkerInfo(this, fromPersists);
        }

        public String getWorkerUUID() {
            return workerUUID;
        }

        public WorkerPersists getWorkerPersists() {
            return workerPersists;
        }

        public boolean isActive() {
            return getWorkerManager().hasActiveWorker(this);
        }

        public boolean isRegistered() {
            return isActive() || getWorkerManager().hasPersistsWorker(this);
        }

        public void registerWorker(Protocol.WorkerInfo workerInfo) {
            getWorkerManager().updateWorkerInfo(this, workerInfo, true, true);
        }

        public void setDeactivated() {
            getWorkerManager().removeActiveWorker(this);
        }

        private WorkerManager getWorkerManager() {
            return Helpers.getWorkerManager();
        }
    }

    public WorkerManager() {
        activeSessionWorker = new ConcurrentHashMap<>();
    }

    public static WorkerSessionInfo createWorkerSessionInfo(Protocol.WorkerInfo workerInfo) {
        return new WorkerSessionInfo(workerInfo.getWorkerId());
    }

    public void updateWorkerInfo(WorkerSessionInfo workerInfo, Protocol.WorkerInfo workerInfoData, boolean updateFullInfo, boolean updatePersists) {
        if(updateFullInfo || !hasActiveWorker(workerInfo)) {
            activeSessionWorker.put(workerInfo.getWorkerUUID(), workerInfoData);
        }

        if(updatePersists) {
            workerInfo.getWorkerPersists().updateWorkerInfo(workerInfoData);
        }
    }

    public void removeActiveWorker(WorkerSessionInfo workerSessionInfo) {
        activeSessionWorker.remove(workerSessionInfo.getWorkerUUID());
        if(WorkerProcess.workerSocketSession.containsKey(workerSessionInfo.getWorkerUUID())) {
            DefaultWebSocketServerSession webSocketServerSession = WorkerProcess.workerSocketSession.get(workerSessionInfo.getWorkerUUID());
            if(WebSocketUtil.isSocketActive(webSocketServerSession)) {
                WebSocketUtil.closeWebSocket(webSocketServerSession, CloseReason.Codes.NORMAL, "Closed by request");
            }
        }
    }

    private boolean hasActiveWorker(WorkerSessionInfo workerInfo) {
        return activeSessionWorker.containsKey(workerInfo.getWorkerUUID());
    }

    private boolean hasPersistsWorker(WorkerSessionInfo workerInfo) {
        try {
            return workerInfo.getWorkerPersists().getWorkerInfo() != null;
        } catch (Exception _) {
            return false;
        }
    }

    @Nullable
    private Protocol.WorkerInfo getWorkerInfo(WorkerSessionInfo workerInfo, boolean fromPersists) {
        if(hasActiveWorker(workerInfo)) {
            return activeSessionWorker.get(workerInfo.getWorkerUUID());
        } else if(fromPersists) {
            try {
                Protocol.WorkerInfo persistWorkerInfo = workerInfo.getWorkerPersists().getWorkerInfo();
                if(persistWorkerInfo != null) {
                    activeSessionWorker.put(workerInfo.getWorkerUUID(), persistWorkerInfo);
                    return persistWorkerInfo;
                }
            } catch (Exception _) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void onServiceAttache() {

    }

    @Override
    public void onServiceDetache() {

    }
}
