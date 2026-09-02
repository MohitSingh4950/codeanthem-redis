package com.codeanthem.redis.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes responses in RESP format so real Redis clients understand them.
 *
 * RESP reply types we support:
 *   +OK\r\n              Simple string  (success messages)
 *   -ERR message\r\n     Error
 *   :1000\r\n            Integer
 *   $5\r\nhello\r\n      Bulk string   (an actual value)
 *   $-1\r\n              Nil bulk string (key doesn't exist)
 *   *2\r\n...            Array (used for KEYS, etc.)
 */
public class RespWriter {

    private final OutputStream out;

    public RespWriter(OutputStream out) {
        this.out = out;
    }

    public void writeSimpleString(String s) throws IOException {
        write("+" + s + "\r\n");
    }

    public void writeError(String message) throws IOException {
        write("-ERR " + message + "\r\n");
    }

    public void writeInteger(long value) throws IOException {
        write(":" + value + "\r\n");
    }

    public void writeBulkString(String value) throws IOException {
        if (value == null) {
            write("$-1\r\n"); // Redis "nil"
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        write("$" + bytes.length + "\r\n");
        out.write(bytes);
        write("\r\n");
    }

    public void writeArray(List<String> values) throws IOException {
        write("*" + values.size() + "\r\n");
        for (String v : values) {
            writeBulkString(v);
        }
    }

    private void write(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
