package com.codeanthem.redis.command;

import com.codeanthem.redis.protocol.RespWriter;
import com.codeanthem.redis.store.RedisStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Takes the parsed command tokens (e.g. ["SET", "foo", "bar"]) and executes them
 * against the RedisStore, then writes the RESP-formatted reply.
 *
 * This is effectively a big switch statement -- which is exactly how real Redis's
 * command table works too (just written in C).
 */
@Component
public class CommandExecutor {

    private final RedisStore store;

    public CommandExecutor(RedisStore store) {
        this.store = store;
    }

    public void execute(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens == null || tokens.isEmpty()) {
            writer.writeError("empty command");
            return;
        }

        String command = tokens.get(0).toUpperCase();

        switch (command) {
            case "PING" -> handlePing(tokens, writer);
            case "ECHO" -> handleEcho(tokens, writer);
            case "SET" -> handleSet(tokens, writer);
            case "GET" -> handleGet(tokens, writer);
            case "DEL" -> handleDel(tokens, writer);
            case "EXISTS" -> handleExists(tokens, writer);
            case "EXPIRE" -> handleExpire(tokens, writer);
            case "TTL" -> handleTtl(tokens, writer);
            case "INCR" -> handleIncr(tokens, writer);
            case "DECR" -> handleDecr(tokens, writer);
            case "KEYS" -> handleKeys(writer);
            case "FLUSHALL" -> handleFlushAll(writer);
            case "DBSIZE" -> writer.writeInteger(store.dbSize());
            case "COMMAND" -> writer.writeArray(List.of()); // redis-cli sends this on connect
            default -> writer.writeError("unknown command '" + command + "'");
        }
    }

    private void handlePing(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens.size() > 1) {
            writer.writeBulkString(tokens.get(1)); // PING message -> echoes message back
        } else {
            writer.writeSimpleString("PONG");
        }
    }

    private void handleEcho(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens.size() < 2) {
            writer.writeError("wrong number of arguments for 'echo' command");
            return;
        }
        writer.writeBulkString(tokens.get(1));
    }

    private void handleSet(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens.size() < 3) {
            writer.writeError("wrong number of arguments for 'set' command");
            return;
        }
        String key = tokens.get(1);
        String value = tokens.get(2);

        // Support optional: SET key value EX <seconds>
        if (tokens.size() >= 5 && tokens.get(3).equalsIgnoreCase("EX")) {
            long ttlSeconds = Long.parseLong(tokens.get(4));
            store.setWithTtl(key, value, ttlSeconds);
        } else {
            store.set(key, value);
        }
        writer.writeSimpleString("OK");
    }

    private void handleGet(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens.size() < 2) {
            writer.writeError("wrong number of arguments for 'get' command");
            return;
        }
        writer.writeBulkString(store.get(tokens.get(1)));
    }

    private void handleDel(List<String> tokens, RespWriter writer) throws IOException {
        int deletedCount = 0;
        for (int i = 1; i < tokens.size(); i++) {
            if (store.delete(tokens.get(i))) {
                deletedCount++;
            }
        }
        writer.writeInteger(deletedCount);
    }

    private void handleExists(List<String> tokens, RespWriter writer) throws IOException {
        int count = 0;
        for (int i = 1; i < tokens.size(); i++) {
            if (store.exists(tokens.get(i))) {
                count++;
            }
        }
        writer.writeInteger(count);
    }

    private void handleExpire(List<String> tokens, RespWriter writer) throws IOException {
        if (tokens.size() < 3) {
            writer.writeError("wrong number of arguments for 'expire' command");
            return;
        }
        long seconds = Long.parseLong(tokens.get(2));
        boolean ok = store.expire(tokens.get(1), seconds);
        writer.writeInteger(ok ? 1 : 0);
    }

    private void handleTtl(List<String> tokens, RespWriter writer) throws IOException {
        writer.writeInteger(store.ttl(tokens.get(1)));
    }

    private void handleIncr(List<String> tokens, RespWriter writer) throws IOException {
        writer.writeInteger(store.incr(tokens.get(1)));
    }

    private void handleDecr(List<String> tokens, RespWriter writer) throws IOException {
        writer.writeInteger(store.decr(tokens.get(1)));
    }

    private void handleKeys(RespWriter writer) throws IOException {
        writer.writeArray(store.keys());
    }

    private void handleFlushAll(RespWriter writer) throws IOException {
        store.flushAll();
        writer.writeSimpleString("OK");
    }
}
