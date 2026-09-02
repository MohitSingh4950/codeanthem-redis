package com.codeanthem.redis.server;

import com.codeanthem.redis.command.CommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The TCP listener. Opens a ServerSocket on the configured port and, for every
 * incoming connection, spins up a new thread (via a cached thread pool) running
 * a ClientHandler. This is the "thread-per-client" model -- simple to understand,
 * good enough for learning purposes. (Real Redis is actually single-threaded with
 * an event loop for its core command execution -- that's a further optimization
 * you could explore once this makes sense.)
 */
@Component
public class RedisServer {

    private static final Logger log = LoggerFactory.getLogger(RedisServer.class);

    private final CommandExecutor commandExecutor;
    private final ExecutorService clientThreadPool = Executors.newCachedThreadPool();

    public RedisServer(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /** Blocks forever, accepting client connections. Call this from a dedicated thread. */
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log.info("codeanthem-redis listening on port {}", port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientThreadPool.submit(new ClientHandler(clientSocket, commandExecutor));
            }
        } catch (IOException e) {
            log.error("Server socket error on port {}: {}", port, e.getMessage(), e);
        }
    }
}
