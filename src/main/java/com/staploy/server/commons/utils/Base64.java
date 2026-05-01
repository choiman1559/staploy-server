package com.staploy.server.commons.utils;

public class Base64 {
    public static byte[] decode(String data) {
        return java.util.Base64.getDecoder().decode(data.getBytes());
    }

    public static String encode(byte[] data) {
        return new String(java.util.Base64.getEncoder().encode(data));
    }
}
