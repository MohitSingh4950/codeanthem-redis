package com.codeanthem.redis.server;

import com.codeanthem.redis.command.CommandExecutor;
import com.codeanthem.redis.protocol.RespReader;
import com.codeanthem.redis.protocol.RespWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

/**
 * Handles ONE connected client for its entire lifetime.
 * Each connected client (redis-cli, telnet, your app...) gets its own thread running this loop:
 *
 *   1. Read a command off the socket (RespReader)
 *   2. Execute it against the store (CommandExecutor)
 *   3. Write the reply back (RespWriter)
 *   4. Repeat until the client disconnects
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final CommandExecutor commandExecutor;

    public ClientHandler(Socket socket, CommandExecutor commandExecutor) {
        this.socket = socket;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public void run() {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        log.info("Client connected: {}", clientAddress);

        try (socket) {
            RespReader reader = new RespReader(socket.getInputStream());
            RespWriter writer = new RespWriter(socket.getOutputStream());

            while (true) {
                List<String> command = reader.readCommand();
                if (command == null) {
                    break; // client closed the connection
                }
                if (command.isEmpty()) {
                    continue;
                }
                log.debug("{} -> {}", clientAddress, command);
                commandExecutor.execute(command, writer);
            }
        } catch (IOException e) {
            log.debug("Connection closed for {}: {}", clientAddress, e.getMessage());
        } finally {
            log.info("Client disconnected: {}", clientAddress);
        }
    }
}
