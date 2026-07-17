package com.staploy.server.admin;

public class AdminConst {
    public static final String METADATA_FILE = ".metadata";
    public static final String APP_PATH = "apps";
    public static final String SHARE_ARCH_PATH = "share";

    public static final String SCHEMA_APP_LIST = "$app_list";
    public static final String SCHEMA_APP_VERSIONS = "APP=%s";

    public static final String SCHEMA_PACKAGE_META = "PKG=%s@%s";
    public static final String SCHEMA_PACKAGE_BLOB_TOKEN = "%s-token";

    public static final String SCHEME_GROUP_LIST = "$group_list";
    public static final String SCHEMA_GROUP = "GROUP=%s";
    public static final String PREFIX_QUERY_GROUP = "group:";
    public static final String PREFIX_GROUP_ALL = "all";

    public static final String AUDIT_NO_USER = "$NO_USER";
    public static final String HEADER_KEY_TOKEN = "Authorization";
    public static final String SCHEMA_USER_UUIDS = "$user_uuids";
    public static final String SCHEMA_USER_PASSWD = "$user_password";
    public static final String SCHEMA_USER_METADATA = "$user_metadata";
    public static final String SCHEMA_USER_AUDIT = "$user_audit";
}
