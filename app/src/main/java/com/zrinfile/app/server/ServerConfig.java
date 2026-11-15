package com.zrinfile.app.server;

import java.io.Serializable;

public class ServerConfig implements Serializable {

    public static final String TYPE_HTTP = "http";
    public static final String TYPE_WEB = "web";
    public static final String TYPE_FTP = "ftp";

    public String type = TYPE_HTTP;
    public String root = "/";
    public int port = 8080;
    public boolean auth = false;
    public String user = "zrin";
    public String pass = "";

    public ServerConfig copy() {
        ServerConfig c = new ServerConfig();
        c.type = type;
        c.root = root;
        c.port = port;
        c.auth = auth;
        c.user = user;
        c.pass = pass;
        return c;
    }
}
