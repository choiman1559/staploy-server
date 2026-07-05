package com.staploy.server.registry;

public class RegistryConst {
    public static final String SCHEME_REPOSITORY_LIST = "$repo_list";
    public static final String SCHEME_REPOSITORY_CACHE = "$repo_cache";

    public static final String SCHEME_REGISTRY_APP_LIST = "$reg_app_list";
    public static final String SCHEME_REGISTRY_APP_METADATA = "REG=%s";
    public static final String SCHEME_REGISTRY_APP_TOKEN_HEADER = "TOKEN=";
    public static final String SCHEME_REGISTRY_APP_TOKEN = SCHEME_REGISTRY_APP_TOKEN_HEADER + "%s";
    public static final String PACKAGE_PATH = "registry/%s/%s_%s.tar";
}
