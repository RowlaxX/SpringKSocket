# Spring-K-Socket

Spring-K-Socket is a high-performance Kotlin library designed to simplify and modularize WebSocket management in Spring Boot applications. It replaces rigid, centralized configurations with a modern, annotation-driven, and highly available architecture.

Built on top of `spring-boot-starter-websocket`, it provides the abstractions needed for complex WebSocket scenarios like zero-downtime reconnections and multi-step initialization.

## Key Highlights

- **Horizontal Configuration:** Define WebSockets as standalone beans, similar to `@RestController`.
- **Annotation-Driven:** Use intuitive annotations like `@WebSocketServer`, `@WebSocketClient`, and `@OnMessage`.
- **High Availability (HA):** Intelligent "bridge" reconnection logic ensures zero message loss during socket handover.
- **Initialization Pipeline:** Build complex handshake and authentication flows using a chain of handlers.
- **Flexible Injection:** Method-level dependency injection for `WebSocket`, `WebSocketAttributes`, and custom payloads.

---

# Why Spring-K-Socket?

Standard Spring WebSocket implementations often lead to "Vertical Configuration"—a single, massive `@Configuration` class that becomes unmanageable as your project grows. Manual handling of reconnections, message synchronization, and "keep-alive" logic is error-prone and tedious.

Spring-K-Socket enables **Modular WebSocket Design**, allowing each socket's logic to live in its own component, fully integrated with the Spring lifecycle.

---

# Core Features

### 1. Modular Bean Configuration
Stop stuffing every socket into one config file. Each WebSocket endpoint is its own Spring bean.

### 2. High Availability (HA) & Perpetual Connections
Many external providers force-close connections after a set duration (e.g., 24 hours). Spring-K-Socket monitors these timeouts and establishes a "bridge" connection shortly before the old one dies.

- **Zero-Downtime:** The new connection is ready before the old one is closed.
- **Message Deduplication:** Automatic deduplication during the handover period ensures each message is processed exactly once.

### 3. Initialization Handlers
Need to send an auth token or a "subscribe" JSON before the socket is "ready"? Use the initialization hooks to automate your handshake logic before passing control to the main handler.

### 4. Smart Injection
Handlers support flexible method signatures. Inject only what you need:
- `WebSocket`: The raw socket instance.
- `WebSocketAttributes`: Type-safe metadata storage for the session.
- `Any`: Automatically deserialized message payloads.

---

# Getting Started

## 1. Enable the Library
Annotate your configuration class with `@EnableKSocket`.

```kotlin
@Configuration
@EnableKSocket
class MyWebSocketConfig
```

## 2. Usage Example - External Market Data (HA Client)

```kotlin
/*
 * A new connection will be opened 23 hours after the connection has been opened.
 * A message deduplicator ensures no message is processed twice during the handover.
 */
@WebSocketClient(
    url = "wss://api.exchange.com/stream",
    shiftDuration = "PT23H", 
    switchDuration = "PT3S",
    initializers = [MarketDataSubscriber::class],
    defaultSerializer = JsonLinesSerializer::class,
    defaultDeserializer = JsonLinesDeserializer::class,
)
class MarketDataClient {
    // Allows access to the perpetual socket from outside the callbacks
    private val perp = AutoPerpetualWebSocket() 
     
    @OnAvailable
    fun onAvailable() = println("Stream Connected & Authenticated")
    
    @OnMessage
    fun onTrade(trade: Trade) {
        println("New trade received: $trade")
    }
}

@Component
class MarketDataSubscriber {
    @OnAvailable
    fun onConnected(ws: WebSocket) {
        // Automatically serialized to the format expected by the server
        ws.sendMessageAsync(SubscribeRequest(topic = "BTCUSDT"))
    }
    
    @OnMessage
    fun onSubscribed(ws: WebSocket, response: SubscribeResponse) {
        if (response.isSuccess) {
            ws.completeHandlerAsync() // Transition to the main handler
        }
    }
}
```

## 3. Usage Example - Chat Server with Rooms

```kotlin
object ChatRoom : WebSocketAttribute<String>()

@WebSocketServer(
    paths = ["/chat"],
    initializers = [ChatRoomJoinInitializer::class],
    initTimeout = "PT10S"
)
class ChatServer {
    private val members = AutoWebSocketCollection()
    
    @OnAvailable
    fun onJoined(ws: WebSocket) {
        val room = ws.attributes[ChatRoom]
        members.send(JoinedMessage(ws.id)) { it.attributes[ChatRoom] == room }
    }
    
    @OnMessage
    fun onMessage(ws: WebSocket, msg: ChatMessage) {
        val room = ws.attributes[ChatRoom]
        members.send(msg) { it.attributes[ChatRoom] == room }
    }
}

@Component
class ChatRoomJoinInitializer {
    @OnMessage
    fun joinRoom(ws: WebSocket, request: JoinRequest) {
        ws.attributes[ChatRoom] = request.room
        ws.completeHandlerAsync()
    }
} 
```

---

# Message Serialization & Deserialization

Spring-K-Socket uses a flexible serialization system to convert your objects to WebSocket-friendly formats (String or ByteArray) and vice versa.

> **Note:** For deserializers, the input `Any` is always either a `String` (text frame) or a `ByteArray` (binary frame).

### 1. Define your Serializers
Implement `WebSocketSerializer` and `WebSocketDeserializer` and register them as Spring beans.

```kotlin
@Component
class JsonSerializer(private val objectMapper: ObjectMapper) : WebSocketSerializer {
    override fun toStringOrByteArray(obj: Any): Any = objectMapper.writeValueAsString(obj)
}

@Component
class JsonDeserializer(private val objectMapper: ObjectMapper) : WebSocketDeserializer {
    override fun fromStringOrByteArray(obj: Any): Any {
        // obj is either a String or a ByteArray
        return if (obj is String) objectMapper.readValue(obj, MyTargetClass::class.java)
        else obj
    }
}
```

### 2. Configure Defaults
Set the default serialization logic for an entire socket using the `@WebSocketServer` or `@WebSocketClient` annotation.

```kotlin
@WebSocketClient(
    url = "...",
    defaultSerializer = JsonSerializer::class,
    defaultDeserializer = JsonDeserializer::class
)
class MyClient
```

### 3. Override per Handler
Individual handlers (including initializers) can override the default serializers using `@WebSocketHandlerProperties`.

```kotlin
@Component
@WebSocketHandlerProperties(serializer = RawSerializer::class)
class MySpecialHandler {
    // Custom logic here
}
```

---

# Advanced Usage

### Handling Handshakes
On the server-side, you can intercept the HTTP handshake using `@BeforeHandshake` and `@AfterHandshake`.

```kotlin
@WebSocketServer(paths = ["/secure"])
class SecureServer {
    @BeforeHandshake
    fun validate(request: ServerHttpRequest): Boolean {
        return request.headers.containsKey("X-Auth-Token")
    }
}
```

### Accessing Sockets Globally
Use `AutoPerpetualWebSocket` (for clients) or `AutoWebSocketCollection` (for servers) to send messages from services or controllers outside the handler callbacks.

```kotlin
@Service
class NotificationService(private val chatServer: ChatServer) {
    fun notify(msg: String) {
        // chatServer uses AutoWebSocketCollection internally
        chatServer.broadcast(msg) 
    }
}
```

---

# Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

# License

Distributed under the MIT License. See `LICENSE` for more information.
