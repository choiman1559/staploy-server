package com.staploy.server.commons.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.staploy.server.commons.utils.IOUtils;
import com.staploy.server.commons.utils.Log;
import com.staploy.server.packet.PacketProcessModel;
import com.staploy.server.packet.PacketWrapper;
import com.staploy.server.admin.AdminProcess;
import com.staploy.server.worker.WorkerProcess;
import io.ktor.http.HttpStatusCode;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class Service {
    private volatile static Service instance;
    private static final String LogTAG = "Service";

    private String serverUUID;
    private final Argument argument;
    public HashMap<String, PacketProcessModel> processModels;
    public Helpers initHelperModules;

    public interface onPacketProcessReplyReceiver {
        void onPacketReply(ApplicationCall call, HttpStatusCode code, String data);
    }

    public onPacketProcessReplyReceiver mOnPacketProcessReplyReceiver;

    private Service(Argument argument) {
        this.argument = argument;
    }

    public static synchronized void configureServiceInstance(Argument argument) throws IOException {
        instance = new Service(argument);
        instance.processModels = new HashMap<>();
        instance.processModels.put(ServiceConsts.CONN_TYPE_WORKER, new WorkerProcess());
        instance.processModels.put(ServiceConsts.CONN_TYPE_ADMIN, new AdminProcess());

        instance.configureStaticModules();
        instance.configureUUID();
        Log.print(LogTAG, "Starting Staploy service instance, UUID: " + instance.serverUUID);
    }

    private void configureStaticModules() {
        initHelperModules = Helpers.getInstance();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void configureUUID() throws IOException {
        File file = new File(argument.baseDir, ServiceConsts.PATH_UUID_STORE_CONF);
        if(!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        if(file.exists()) {
            String readFile = IOUtils.readFrom(file);
            if(!readFile.isEmpty()) {
                String[] lines = readFile.split("\n");
                if(lines.length == 2 && lines[0].startsWith("###") && !lines[1].isEmpty()) {
                    serverUUID = lines[1].trim();
                    return;
                }
            }
            serverUUID = UUID.randomUUID().toString();
            IOUtils.writeTo(file, String.format("%s%s", ServiceConsts.UUID_CONF_WARNING ,serverUUID), true);
        } else {
            serverUUID = UUID.randomUUID().toString();
            IOUtils.writeTo(file, String.format("%s%s", ServiceConsts.UUID_CONF_WARNING ,serverUUID));
        }
    }

    public String getServerUUID() {
        return serverUUID;
    }

    public static synchronized Service getInstance() {
        if (instance == null) {
            throw new NullPointerException("Service Instance is not initialized!");
        }
        return instance;
    }

    public static void invokeProcessWebSocketPacket(ApplicationCall applicationCall, String connectionType, DefaultWebSocketServerSession socketServerSession) throws Exception {
        instance.checkConnectionTypeOrThrow(connectionType);
        PacketProcessModel packetProcessModel = instance.processModels.get(connectionType);
        if (packetProcessModel != null) {
            packetProcessModel.onWebSocketSessionConnected(applicationCall, connectionType, socketServerSession);
        }
    }

    public static void invokeProcessPacket(ApplicationCall applicationCall, String connectionType, String rawData) throws Exception {
        instance.checkConnectionTypeOrThrow(connectionType);
        PacketProcessModel packetProcessModel = instance.processModels.get(connectionType);

        if (rawData == null || rawData.isEmpty()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("HTTP Request body is null", HttpStatusCode.Companion.getNoContent(), ""));
        } else if (packetProcessModel != null) {
            packetProcessModel.onPacketReceived(applicationCall, connectionType, rawData);
        }
    }

    public static void replyPacket(ApplicationCall call, PacketWrapper data) throws InvalidProtocolBufferException {
        Service mInstance = Service.getInstance();
        if (mInstance != null && mInstance.mOnPacketProcessReplyReceiver != null) {
            mInstance.mOnPacketProcessReplyReceiver.onPacketReply(call, data.getStatusCode(), data.getSerializedData());
        }
    }

    public void checkConnectionTypeOrThrow(String connectionType) throws IllegalAccessException {
        if(!processModels.containsKey(connectionType)) {
            throw new IllegalAccessException(ServiceConsts.ERROR_CONN_TYPE_NOT_FOUND);
        }
    }

    public Argument getArgument() {
        return argument;
    }
}
