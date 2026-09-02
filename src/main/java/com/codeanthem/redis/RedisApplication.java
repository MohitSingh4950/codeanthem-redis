package com.codeanthem.redis;

import com.codeanthem.redis.server.RedisServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

/**
 * Entry point. Spring Boot's job here is just dependency injection (it wires
 * RedisServer -> CommandExecutor -> RedisStore together automatically because
 * they're all @Component-annotated) and reading application.properties.
 *
 * The actual "being a Redis server" logic lives in the server/, protocol/,
 * store/, and command/ packages -- Spring Boot is just the glue and bootstrap.
 */
@SpringBootApplication
public class RedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisApplication.class, args);
    }

    /**
     * A CommandLineRunner is Spring Boot's hook for "run this code once, right after
     * the application context is fully wired up." We use it to start our TCP server
     * on a background daemon thread -- because RedisServer.start() blocks forever
     * in its accept() loop, and we don't want that to block Spring's own startup thread.
     */
    @Component
    static class ServerStarter implements CommandLineRunner {

        private final RedisServer redisServer;

        @Value("${codeanthem.redis.port}")
        private int port;

        ServerStarter(RedisServer redisServer) {
            this.redisServer = redisServer;
        }

        @Override
        public void run(String... args) {
            Thread serverThread = new Thread(() -> redisServer.start(port), "redis-server-thread");
            serverThread.setDaemon(false); // keep JVM alive as long as the server is running
            serverThread.start();
        }
    }
}
