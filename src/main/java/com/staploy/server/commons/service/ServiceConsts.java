package com.staploy.server.commons.service;

import com.staploy.server.admin.AdminConst;

public class ServiceConsts {
    public final static String API_ROUTE_SCHEMA = "/api/{version}/{connection_type}";
    public final static String CONN_TYPE_ADMIN = "admin";
    public final static String CONN_TYPE_WORKER = "worker";
    public final static String CONN_TYPE_REGISTRY = "registry";

    public final static String PATH_BLOB_DIR = "/blob/";
    public final static String PATH_CACHE_DIR = "/cache/";
    public final static String PATH_REGISTRY_DIR = "/registry/";
    public final static String PATH_APPS_DIR = "/" + AdminConst.APP_PATH + "/";

    public final static String SCHEMA_BLOB_LIST = "$blob_list";
    public static final String BLOB_REQ_TYPE = "blob_req_type";
    public static final String BLOB_REQ_TYPE_UPLOAD = "type_upload";
    public static final String BLOB_REQ_TYPE_DOWNLOAD = "type_download";

    public final static String PATH_UUID_STORE_CONF = "/.STAPLOY_UUID";
    public final static String UUID_CONF_WARNING = "### UUID KEY FOR STAPLOY SERVER, DO NOT EDIT OR DELETE THIS FILE AT YOUR OWN ###\n";
    public static final String SCHEMA_SERVER_UUID = "$server_uuid";

    public final static String STATUS_ERROR = "error";
    public final static String STATUS_OK = "ok";

    public final static String JWT_CLAIM_UUID = "uuid";
    public final static String JWT_CLAIM_USERNAME= "username";
    public final static String JWT_CLAIM_VERSION = "version";
    public final static String JWT_CLAIM_PERMISSION = "permission";

    public final static String ERROR_NONE = "none";
    public final static String ERROR_NOT_FOUND = "not_found";
    public final static String ERROR_CONN_TYPE_NOT_FOUND = "connection_type_not_found";
    public final static String ERROR_CONN_TYPE_NOT_IMPLEMENTED = "connection_type_not_implemented";
    public final static String ERROR_INTERNAL_ERROR = "server_internal_error";
    public final static String ERROR_ILLEGAL_ARGUMENT = "server_illegal_argument";
    public final static String ERROR_TOKEN_NOT_VALID = "token_not_valid";
}
