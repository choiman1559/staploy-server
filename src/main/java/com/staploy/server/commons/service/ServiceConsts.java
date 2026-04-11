package com.staploy.server.commons.service;

public class ServiceConsts {
    public final static String API_ROUTE_SCHEMA = "/api/{version}/{connection_type}";
    public final static String CONN_TYPE_ADMIN = "admin";
    public final static String CONN_TYPE_NODE = "node";

    public final static String STATUS_ERROR = "error";
    public final static String STATUS_OK = "ok";

    public final static String ERROR_NONE = "none";
    public final static String ERROR_NOT_FOUND = "not_found";
    public final static String ERROR_CONN_TYPE_NOT_FOUND = "connection_type_not_found";
    public final static String ERROR_CONN_TYPE_NOT_IMPLEMENTED = "connection_type_not_implemented";
    public final static String ERROR_INTERNAL_ERROR = "server_internal_error";
    public final static String ERROR_ILLEGAL_ARGUMENT = "server_illegal_argument";
}
