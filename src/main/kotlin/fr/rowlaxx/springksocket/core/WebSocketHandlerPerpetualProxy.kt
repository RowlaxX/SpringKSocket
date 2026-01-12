package fr.rowlaxx.springksocket.core

import fr.rowlaxx.springksocket.model.*

class WebSocketHandlerPerpetualProxy(
    private val acceptOpeningConnection: (WebSocket) -> Unit,
    private val acceptMessage: (WebSocket, Any) -> Unit,
    private val acceptClosingConnection: (WebSocket) -> Unit,
    private val perpetualWebSocket: PerpetualWebSocket,
) : WebSocketHandler {
    override val deserializer: WebSocketDeserializer get() = WebSocketDeserializer.Passthrough // We let the PerpetualHandler handle deduplication
    override val serializer: WebSocketSerializer get() = perpetualWebSocket.handler.serializer

    override fun onAvailable(webSocket: WebSocket) {
        acceptOpeningConnection(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, msg: Any) {
        acceptMessage(webSocket, msg)
    }

    override fun onUnavailable(webSocket: WebSocket) {
        if (webSocket.isInitialized()) { // When handlerChain.size == !
            acceptClosingConnection(webSocket)
        }
    }
}