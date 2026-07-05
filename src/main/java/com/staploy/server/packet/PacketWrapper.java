package com.staploy.server.packet;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;
import com.staploy.*;
import com.staploy.server.commons.service.Service;
import com.staploy.server.commons.service.ServiceConsts;
import io.ktor.http.HttpStatusCode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public class PacketWrapper {
    private HttpStatusCode statusCode;
    private Admin.ResponsePacket responsePacket;

    private static final TypeRegistry AUDIT_TYPE_REGISTRY = TypeRegistry.newBuilder()
            .add(Admin.RequestPacket.getDescriptor())
            .add(Admin.ResponsePacket.getDescriptor())
            .build();

    public void setStatusCode(HttpStatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public void setResponsePacket(Admin.ResponsePacket responsePacket) {
        this.responsePacket = responsePacket;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public Admin.ResponsePacket getResponsePacket() {
        return responsePacket;
    }

    public String getSerializedData() throws InvalidProtocolBufferException {
        return JsonFormat.printer().usingTypeRegistry(AUDIT_TYPE_REGISTRY).print(getResponsePacket());
    }

    public static PacketWrapper makePacket(String extra_data) {
        return makePacket(extra_data, new Protocol.WorkerPacket[]{});
    }

    public static PacketWrapper makePacket(String extra_data, Users.UserResponsePacket userResponsePacket) {
        return makePacket(extra_data, null, null, userResponsePacket, null);
    }

    public static PacketWrapper makePacket(String extra_data, Registry.RegistryResponsePacket registryResponsePacket) {
        return makePacket(extra_data, null, null, null, registryResponsePacket);
    }

    public static PacketWrapper makePacket(String extra_data, Protocol.WorkerPacket... workerPackets) {
        return makePacket(extra_data, List.of(workerPackets));
    }

    public static PacketWrapper makePacket(String extra_data, Admin.GroupResponsePacket... groupPackets) {
        return makePacket(extra_data, null, List.of(groupPackets), null, null);
    }

    public static PacketWrapper makePacket(String extra_data, List<Protocol.WorkerPacket> workerPackets) {
        return makePacket(extra_data, workerPackets, null, null, null);
    }

    public static PacketWrapper makePacket(String extra_data,
                                           List<Protocol.WorkerPacket> workerPackets,
                                           List<Admin.GroupResponsePacket> groupResponsePackets,
                                           Users.UserResponsePacket userResponsePacket,
                                           Registry.RegistryResponsePacket registryResponsePacket) {
        PacketWrapper packetWrapper = new PacketWrapper();
        packetWrapper.setStatusCode(HttpStatusCode.Companion.getOK());

        Admin.ResponsePacket.Builder responseBuilder = Admin.ResponsePacket.newBuilder();
        responseBuilder.setStatus(ServiceConsts.STATUS_OK);
        responseBuilder.setErrorCause(ServiceConsts.ERROR_NONE);
        responseBuilder.setExtraData(extra_data);

        if(workerPackets != null) {
            responseBuilder.addAllWorkerResponse(workerPackets);
        }

        if(groupResponsePackets != null) {
            responseBuilder.addAllGroupResponse(groupResponsePackets);
        }

        if(userResponsePacket != null) {
            responseBuilder.setUserResponse(userResponsePacket);
        }

        if(registryResponsePacket != null) {
            responseBuilder.setRegistryResponse(registryResponsePacket);
        }

        packetWrapper.setResponsePacket(responseBuilder.build());
        return packetWrapper;
    }

    public static PacketWrapper makeErrorPacket(String message) {
        return makeErrorPacket(message, "");
    }

    public static PacketWrapper makeErrorPacket(String message, String extraDescription) {
        return makeErrorPacket(message, HttpStatusCode.Companion.getInternalServerError(), extraDescription);
    }

    public static PacketWrapper makeErrorPacket(String message, HttpStatusCode statusCode, String extraDescription) {
        PacketWrapper packetWrapper = new PacketWrapper();
        packetWrapper.setStatusCode(statusCode);

        Admin.ResponsePacket.Builder responseBuilder = Admin.ResponsePacket.newBuilder();
        responseBuilder.setStatus(ServiceConsts.STATUS_ERROR);
        responseBuilder.setErrorCause(message);
        responseBuilder.setExtraData(extraDescription);

        packetWrapper.setResponsePacket(responseBuilder.build());
        return packetWrapper;
    }

    public static Protocol.Packet.Builder createNewPacket(Protocol.ProtocolProcedure protocolProcedure, Protocol.ActionProcedure actionProcedure) {
        return createNewPacket(protocolProcedure, actionProcedure, null);
    }

    public static Protocol.Packet.Builder createNewPacket(Protocol.ProtocolProcedure protocolProcedure, Protocol.ActionProcedure actionProcedure, @Nullable String extraData) {
        Protocol.Packet.Builder packetBuilder = Protocol.Packet.newBuilder();
        packetBuilder.setProcedure(protocolProcedure);
        packetBuilder.setActionProcedure(actionProcedure);
        packetBuilder.setChallengeCode(String.format("%d_%d_%d",
                protocolProcedure.getNumber(),
                actionProcedure.getNumber(),
                Random.from(RandomGenerator.getDefault()).nextInt()));

        if(extraData != null && !extraData.isEmpty()) {
            packetBuilder.setExtraData(extraData);
        }
        return packetBuilder;
    }

    public static Protocol.ServerPacket.Builder createNewServerPacket(Protocol.Packet packet, @Nullable List<App.AppInfoFetch> appInfoFetch) {
        Protocol.ServerPacket.Builder packetBuilder = Protocol.ServerPacket.newBuilder();
        packetBuilder.setServerUUID(Service.getInstance().getServerUUID());
        packetBuilder.setPacketInfo(packet);
        if(appInfoFetch != null) {
            packetBuilder.addAllAppInfoFetch(appInfoFetch);
        }
        return packetBuilder;
    }
}
