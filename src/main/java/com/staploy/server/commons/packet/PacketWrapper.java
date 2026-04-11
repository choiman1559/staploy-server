package com.staploy.server.commons.packet;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.staploy.Admin;
import com.staploy.server.commons.service.ServiceConsts;
import io.ktor.http.HttpStatusCode;
import org.jetbrains.annotations.Nullable;

public class PacketWrapper {
    private HttpStatusCode statusCode;
    private Admin.ResponsePacket responsePacket;

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
        return JsonFormat.printer().print(getResponsePacket());
    }

    public static PacketWrapper makePacket(byte[]... extra_data) {
        PacketWrapper packetWrapper = new PacketWrapper();
        packetWrapper.setStatusCode(HttpStatusCode.Companion.getOK());

        Admin.ResponsePacket.Builder responseBuilder = Admin.ResponsePacket.newBuilder();
        responseBuilder.setStatus(ServiceConsts.STATUS_OK);
        responseBuilder.setErrorCause(ServiceConsts.ERROR_NONE);

        for (byte[] extraDatum : extra_data) {
            responseBuilder.addExtraData(ByteString.copyFrom(extraDatum));
        }

        packetWrapper.setResponsePacket(responseBuilder.build());
        return packetWrapper;
    }

    public static PacketWrapper makeErrorPacket(String message) {
        return makeErrorPacket(message, new byte[][]{});
    }

    public static PacketWrapper makeErrorPacket(String message, byte[] @Nullable ... extraDescription) {
        return makeErrorPacket(message, HttpStatusCode.Companion.getInternalServerError(), extraDescription);
    }

    public static PacketWrapper makeErrorPacket(String message, HttpStatusCode statusCode, byte[] @Nullable ... extraDescription) {
        PacketWrapper packetWrapper = new PacketWrapper();
        packetWrapper.setStatusCode(statusCode);

        Admin.ResponsePacket.Builder responseBuilder = Admin.ResponsePacket.newBuilder();
        responseBuilder.setStatus(ServiceConsts.STATUS_ERROR);
        responseBuilder.setErrorCause(message);

        if(extraDescription != null) for (byte[] extraDatum : extraDescription) {
            if(extraDatum != null) {
                responseBuilder.addExtraData(ByteString.copyFrom(extraDatum));
            }
        }

        packetWrapper.setResponsePacket(responseBuilder.build());
        return packetWrapper;
    }
}
