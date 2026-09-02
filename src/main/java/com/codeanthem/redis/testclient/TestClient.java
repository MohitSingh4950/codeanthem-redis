package com.codeanthem.redis.testclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A tiny standalone client for testing your running RedisApplication server.
 * It does NOT use Spring -- it's just a plain socket client, so you can run it
 * as its own "Run Configuration" in IntelliJ, separate from the server.
 *
 * HOW TO USE:
 *   1. Make sure RedisApplication is already running (separate Run Configuration/tab).
 *   2. Right-click this file -> Run 'TestClient.main()'.
 *   3. Watch the console -- it sends a sequence of real commands and prints the replies.
 *
 * It sends "inline commands" (plain text lines) which our RespReader also understands,
 * so you don't need to hand-build RESP arrays to test manually.
 */
public class TestClient {

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = "localhost";
        int port = 6379;

        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            System.out.println("Connected! Running a sequence of test commands:\n");

            send(out, in, "PING");
            send(out, in, "SET foo bar");
            send(out, in, "GET foo");
            send(out, in, "EXISTS foo");
            send(out, in, "EXPIRE foo 100");
            send(out, in, "TTL foo");
            send(out, in, "INCR counter");
            send(out, in, "INCR counter");
            send(out, in, "INCR counter");
            send(out, in, "GET counter");
            send(out, in, "KEYS");
            send(out, in, "DEL foo");
            send(out, in, "GET foo");
            send(out, in, "DBSIZE");

            System.out.println("\nAll test commands sent. If you saw replies above (PONG, OK, bar, etc.) your server works.");
        }
    }

    /**
     * Sends one command as a line of plain text (our server's "inline command" mode),
     * then reads and prints exactly one line of the reply.
     *
     * Note: this simple line-based reading works for our test commands above because
     * their replies are all single-line (+OK, :1, $-1 etc. minus the bulk-string payload
     * line which we also read). It's intentionally simple -- not a full RESP client.
     */
    private static void send(OutputStream out, BufferedReader in, String command) throws IOException {
        System.out.println(">> " + command);
        out.write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        String replyLine = in.readLine();
        if (replyLine == null) {
            System.out.println("<< (no reply -- connection closed)");
            return;
        }

        // If it's a bulk string ($<len>), the actual value is on the NEXT line
        // (unless it's the nil reply "$-1", which has no follow-up line).
        if (replyLine.startsWith("$") && !replyLine.equals("$-1")) {
            String valueLine = in.readLine();
            System.out.println("<< " + replyLine + " -> \"" + valueLine + "\"");
        } else if (replyLine.startsWith("*")) {
            // Array reply (e.g. KEYS): read that many bulk-string pairs
            int count = Integer.parseInt(replyLine.substring(1).trim());
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < count; i++) {
                String lenLine = in.readLine();      // $<len>
                String valueLine = in.readLine();    // actual value
                items.append(valueLine).append(i < count - 1 ? ", " : "");
            }
            System.out.println("<< [" + items + "]");
        } else {
            System.out.println("<< " + replyLine);
        }
    }
}
