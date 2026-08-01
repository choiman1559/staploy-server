package com.staploy.server.commons.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

@SuppressWarnings({"SameParameterValue", "unused"})
public class Argument {

    public int adminPort;
    public int workerPort;

    public String host;
    public boolean isDebug;
    public String baseDir;

    public boolean useBlobCache = false;
    public long maxBlobCacheSize = 65536;
    public int maxBlobCacheEntities = 10;

    public boolean enableRegistry;
    public boolean allowPullNonPermit;
    public boolean allowPullAnonymous;

    public boolean allowNonUser;
    public boolean enforceJwtAuth = true;
    public String jwtAuthPrivateKey;
    public String jwtAuthPublicKey;

    public boolean useWorkerMtls;
    public String mTlsCaCert;
    public String mTlsChain;
    public String mTlsKey;

    public boolean useAdminTls;
    public String adminTlsChain;
    public String adminTlsKey;

    public String redisAddress;
    public int redisPort;
    public String redisPassword;
    public boolean redisUseSSL;
    public int redisDatabaseNum = 0;

    public static Argument buildFrom(List<String> argument) throws IOException, IllegalArgumentException {
        if (argument.isEmpty()) {
            throw new IllegalArgumentException("argument is not found!");
        }

        File file = new File(argument.getFirst());
        if (file.exists() && file.canRead()) {
            return (Argument) parsePropertiesFromFile(file.getPath(), Argument.class);
        } else {
            throw new FileNotFoundException("com.staploy.server.commons.service.Argument File not found or Not Accessible");
        }
    }

    private static Object parsePropertiesFromFile(String filePath, Class<?> cls) throws IOException {
        Properties fileProps = new Properties();
        fileProps.load(new FileInputStream(filePath));

        final ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(fileProps, cls);
    }
}
