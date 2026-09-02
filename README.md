# codeanthem-redis

A minimal, working Redis server clone written in Java, wired up with Spring Boot,
speaking the **real RESP protocol** — so it works with `redis-cli`, `telnet`, or any
real Redis client library.

## How it fits together

```
Client (redis-cli) 
      |
      v
RedisServer (server/)        <- ServerSocket, accepts connections, one thread per client
      |
      v
ClientHandler (server/)      <- read -> execute -> reply loop for ONE client
      |
      v
RespReader (protocol/)       <- parses RESP bytes into ["SET","foo","bar"]
      |
      v
CommandExecutor (command/)   <- big switch: SET -> store.set(), GET -> store.get()...
      |
      v
RedisStore (store/)          <- ConcurrentHashMap, the actual "database"
      |
      v
RespWriter (protocol/)       <- encodes the result back into RESP bytes ("+OK\r\n" etc.)
```

Spring Boot's ONLY job here is dependency injection (wiring these classes together
automatically via `@Component`) and reading `application.properties`. The actual
"being Redis" logic is 100% plain Java — sockets, threads, and a hash map. That's
intentional: it's what makes this understandable and portable to non-Spring Java too.

## Why this design?

- **RESP protocol** (`protocol/RespReader.java`, `protocol/RespWriter.java`): Real Redis
  clients don't send plain text — they send a specific binary-safe wire format. If you
  don't speak it, `redis-cli` can't talk to you. We implement just enough of it: arrays,
  bulk strings, simple strings, errors, integers.
- **Thread-per-client** (`server/RedisServer.java`): Every connected client gets its own
  thread from a cached pool. Simple mental model: one thread = one client's whole
  conversation. (Real Redis is actually single-threaded with an event loop for speed —
  a good next thing to explore once this clicks.)
- **ConcurrentHashMap store** (`store/RedisStore.java`): Since multiple client threads can
  hit the store at once, it needs to be thread-safe. A background daemon thread sweeps
  expired keys every second, mimicking Redis's own active-expiry cycle.

## Commands supported

`PING`, `ECHO`, `SET key value [EX seconds]`, `GET`, `DEL`, `EXISTS`, `EXPIRE`, `TTL`,
`INCR`, `DECR`, `KEYS`, `FLUSHALL`, `DBSIZE`

## How to run it

### Option A — IntelliJ / Eclipse
1. Open the folder as a Maven project (IDE will detect `pom.xml` and download dependencies).
2. Run `RedisApplication.java` (it has a `main` method).
3. You should see a log line: `codeanthem-redis listening on port 6379`

### Option B — command line
```bash
mvn spring-boot:run
```

### Option C — build a jar and run it
```bash
mvn clean package
java -jar target/codeanthem-redis-1.0.0.jar
```

## How to test it

**With redis-cli** (if you have real Redis tools installed):
```bash
redis-cli -p 6379
127.0.0.1:6379> PING
PONG
127.0.0.1:6379> SET foo bar
OK
127.0.0.1:6379> GET foo
"bar"
127.0.0.1:6379> EXPIRE foo 5
(integer) 1
127.0.0.1:6379> TTL foo
(integer) 5
```

**With telnet** (no Redis tools needed — uses the "inline command" fallback):
```bash
telnet localhost 6379
PING
SET foo bar
GET foo
```

## A note on why the build might fail for you

If your original project didn't compile, the most common causes for a fresh
Spring Boot Maven project are:
1. **JDK version mismatch** — this project targets Java 17 (`<java.version>17</java.version>`
   in `pom.xml`). Check your installed JDK matches (`java -version`) and that your IDE's
   Project SDK is set to 17+.
2. **Dependencies not downloaded** — first build needs internet access to Maven Central.
   If you're behind a corporate proxy/VPN, Maven may be silently failing to resolve
   `spring-boot-starter-parent`. Run `mvn clean install -X` and check for `Could not
   resolve dependencies` in the output.
3. **IDE not recognizing it as a Maven project** — right-click `pom.xml` → "Add as Maven
   Project" (IntelliJ) or "Enable Auto-Import" if the button/banner is greyed out.

Send me the exact error text if it still fails and I'll pinpoint it precisely.
