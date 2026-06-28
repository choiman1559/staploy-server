package com.staploy.server.commons.utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class Base64 {
    public static byte[] decode(String data) {
        return java.util.Base64.getDecoder().decode(data.getBytes());
    }

    public static String encode(byte[] data) {
        return new String(java.util.Base64.getEncoder().encode(data));
    }

    public static String encodeBcrypt(byte[] data) {
        return new String(BCrypt.withDefaults().hash(12, data));
    }

    public static boolean validateBcrypt(byte[] input, String stored) {
        return BCrypt.verifyer().verify(input, stored.getBytes()).verified;
    }
}
