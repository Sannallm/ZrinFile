package com.zrinfile.app.server;

public interface FileServer {

    void start() throws Exception;

    void stop();

    boolean isRunning();
}
