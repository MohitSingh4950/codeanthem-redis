package com.codeanthem.redis.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads RESP (REdis Serialization Protocol) commands off the wire.
 *
 * Real Redis clients (redis-cli, Jedis, Lettuce...) send commands as a RESP "array of bulk strings":
 *
 *   *3\r\n            <- array of 3 elements
 *   $3\r\nSET\r\n     <- bulk string, 3 bytes, "SET"
 *   $3\r\nfoo\r\n     <- bulk string, 3 bytes, "foo"
 *   $3\r\nbar\r\n     <- bulk string, 3 bytes, "bar"
 *
 * That's the wire representation of: SET foo bar
 *
 * We ALSO support "inline commands" (plain text like "PING\r\n" or "SET foo bar\r\n")
 * so you can test the server with plain `telnet` / `nc`, not just redis-cli.
 */
public class RespReader {

    private final InputStream in;

    public RespReader(InputStream in) {
        this.in = in;
    }

    /**
     * Reads one full command from the client and returns it as tokens,
     * e.g. ["SET", "foo", "bar"]. Returns null if the client closed the connection.
     */
    public List<String> readCommand() throws IOException {
        int firstByte = in.read();
        if (firstByte == -1) {
            return null; // client disconnected
        }

        if (firstByte == '*') {
            return readArrayCommand();
        } else {
            // Inline command: put the byte back conceptually by treating it as the
            // start of a plain text line, e.g. "PING" or "SET foo bar"
            String restOfLine = readLine();
            String fullLine = ((char) firstByte) + restOfLine;
            List<String> tokens = new ArrayList<>();
            for (String token : fullLine.trim().split("\\s+")) {
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
            return tokens.isEmpty() ? readCommand() : tokens;
        }
    }

    private List<String> readArrayCommand() throws IOException {
        int count = Integer.parseInt(readLine().trim());
        List<String> tokens = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            int typeByte = in.read();
            if (typeByte != '$') {
                throw new IOException("Expected bulk string '$' but got: " + (char) typeByte);
            }
            int length = Integer.parseInt(readLine().trim());
            byte[] data = readExactly(length);
            readLine(); // consume trailing \r\n after the bulk string payload
            tokens.add(new String(data));
        }
        return tokens;
    }

    /** Reads bytes up to (but not including) the next \r\n */
    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.read(); // consume the \n
                break;
            }
            if (b == '\n') {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString();
    }

    private byte[] readExactly(int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading bulk string");
            }
            totalRead += read;
        }
        return buffer;
    }
}
