package fr.rowlaxx.springwebsocketaop.model

import fr.rowlaxx.springwebsocketaop.data.CustomWebSocketServerConfiguration
import fr.rowlaxx.springwebsocketaop.service.io.ServerWebSocketFactory
import fr.rowlaxx.springwebsocketaop.utils.ByteBufferUtils.getBackingArray
import fr.rowlaxx.springwebsocketaop.utils.WebSocketSessionUtils.handleBinaryMessage
import fr.rowlaxx.springwebsocketaop.utils.WebSocketSessionUtils.handleClose
import fr.rowlaxx.springwebsocketaop.utils.WebSocketSessionUtils.handlePongMessage
import fr.rowlaxx.springwebsocketaop.utils.WebSocketSessionUtils.handleTextMessage
import fr.rowlaxx.springwebsocketaop.utils.WebSocketSessionUtils.handleTransportError
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.AbstractWebSocketHandler

@Suppress("UNCHECKED_CAST")
class SpringWebSocketHandler(
    private val config: CustomWebSocketServerConfiguration,
    private val serverWebSocketFactory: ServerWebSocketFactory
) : AbstractWebSocketHandler() {

    override fun handleBinaryMessage(session: WebSocketSession, message: BinaryMessage) {
        session.handleBinaryMessage(message.payload.getBackingArray())
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        session.handleTextMessage(message.payload)
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        session.handlePongMessage()
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        session.handleTransportError(exception)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        session.handleClose(status)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        serverWebSocketFactory.wrap(
            session = session,
            config = config,
        )
    }

}