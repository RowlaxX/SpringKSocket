package fr.rowlaxx.springksocket.service.aop

import fr.rowlaxx.springksocket.annotation.OnAvailable
import fr.rowlaxx.springksocket.annotation.OnMessage
import fr.rowlaxx.springksocket.annotation.OnUnavailable
import fr.rowlaxx.springksocket.data.WebSocketAttributes
import fr.rowlaxx.springksocket.model.WebSocket
import fr.rowlaxx.springksocket.model.WebSocketDeserializer
import fr.rowlaxx.springksocket.model.WebSocketHandler
import fr.rowlaxx.springksocket.model.WebSocketSerializer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WebSocketHandlerFactory(
    private val collectionManager: AutoWebSocketCollectionManager
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val onAvailableInject = arrayOf(WebSocket::class, WebSocketAttributes::class)
    private val onUnavailableInject = arrayOf(WebSocket::class, WebSocketAttributes::class)
    private val onMessageInject = arrayOf(WebSocket::class, WebSocketAttributes::class, Any::class)

    fun extract(bean: Any, serializer: WebSocketSerializer, deserializer: WebSocketDeserializer): WebSocketHandler {
        collectionManager.initializeIfNotDone(bean)

        val available = ReflectionUtils.findMethodsWithAnnotation(bean, OnAvailable::class)
            .map { it.second }
            .map { ReflectionUtils.findInjectionScheme(bean, it, *onAvailableInject) }

        val unavailable = ReflectionUtils.findMethodsWithAnnotation(bean, OnUnavailable::class)
            .map { it.second }
            .map { ReflectionUtils.findInjectionScheme(bean, it, *onUnavailableInject) }

        val onMessage = ReflectionUtils.findMethodsWithAnnotation(bean, OnMessage::class)
            .map { it.second }
            .map { ReflectionUtils.findInjectionScheme(bean, it, *onMessageInject) }

        if (available.isEmpty() && unavailable.isEmpty()) {
            throw IllegalArgumentException("Bean ${bean::class.simpleName} is not a WebSocketHandler. Please add at least one @OnAvailable, @OnUnavailable or @OnMessage method")
        }

        return InternalImplementation(
            serializer = serializer,
            deserializer = deserializer,
            available = available,
            unavailable = unavailable,
            message = onMessage,
            bean = bean
        )
    }

    private inner class InternalImplementation(
        override val deserializer: WebSocketDeserializer,
        override val serializer: WebSocketSerializer,
        private val bean: Any,
        private val available: List<ReflectionUtils.InjectionScheme>,
        private val unavailable: List<ReflectionUtils.InjectionScheme>,
        private val message: List<ReflectionUtils.InjectionScheme>,
    ) : WebSocketHandler {

        override fun onAvailable(webSocket: WebSocket) {
            collectionManager.onAvailable(bean, webSocket)

            val args = arrayOf(webSocket, webSocket.attributes)

            available.forEach {
                runInWS(it, webSocket, *args)
            }
        }

        override fun onMessage(webSocket: WebSocket, msg: Any) {
            val args1 = arrayOf(webSocket, webSocket.attributes, msg)
            var handled = false

            message.filter { ReflectionUtils.canInject(it, *args1) }
                .apply { if (isNotEmpty()) handled = true }
                .forEach { runInWS(it, webSocket, *args1) }

            val deserialized = deserializer.fromStringOrByteArray(msg)

            if (deserialized !== msg) {
                val args2 = arrayOf(webSocket, webSocket.attributes, deserialized)

                message.filter { ReflectionUtils.canInject(it, *args2) }
                    .apply { if (isNotEmpty()) handled = true }
                    .forEach { runInWS(it, webSocket, *args2) }
            }

            if (!handled) {
                log.warn("Unhandled message of type ${deserialized::class.simpleName} in bean ${bean::class.simpleName}")
            }
        }

        override fun onUnavailable(webSocket: WebSocket) {
            collectionManager.onUnavailable(bean, webSocket)

            val args = arrayOf(webSocket, webSocket.attributes)

            unavailable.forEach {
                runInWS(it, webSocket, *args)
            }
        }

        private fun runInWS(scheme: ReflectionUtils.InjectionScheme, ws: WebSocket, vararg args: Any?) {
            runCatching { ReflectionUtils.inject(scheme, *args) }
                .onFailure { log.error("Method ${scheme.method} threw an exception", it) }
                .onSuccess {
                    if (it != null && it != Unit) {
                        ws.sendMessageAsync(it).exceptionally { e ->
                            log.error("Unable to send returned object of type ${it::class.simpleName} : ${e.message}")
                        }
                    }
                }
        }
    }
}