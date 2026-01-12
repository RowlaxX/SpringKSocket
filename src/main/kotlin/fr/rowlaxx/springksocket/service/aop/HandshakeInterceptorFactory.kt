package fr.rowlaxx.springksocket.service.aop

import fr.rowlaxx.springksocket.annotation.AfterHandshake
import fr.rowlaxx.springksocket.annotation.BeforeHandshake
import fr.rowlaxx.springksocket.data.WebSocketAttributes
import fr.rowlaxx.springksocket.util.HttpHeadersUtils.toJavaHeaders
import fr.rowlaxx.springksocket.util.WebSocketMapAttributesUtils
import fr.rowlaxx.springkutils.reflection.utils.MethodInjectionUtils
import fr.rowlaxx.springkutils.reflection.utils.MethodInjectionUtils.toInjectionSupport
import fr.rowlaxx.springkutils.reflection.utils.ReflectionUtils
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Service
class HandshakeInterceptorFactory {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val injectableBefore = arrayOf(WebSocketAttributes::class, ServerHttpRequest::class, ServerHttpResponse::class)
    private val injectableAfter = arrayOf(WebSocketAttributes::class, ServerHttpResponse::class, ServerHttpResponse::class, Exception::class)

    fun extract(bean: Any): HandshakeInterceptor {
        val before = ReflectionUtils.findMethodsWithAnnotation(bean, BeforeHandshake::class)
            .map { it.second.toInjectionSupport() }

        val after = ReflectionUtils.findMethodsWithAnnotation(bean, AfterHandshake::class)
            .map { it.second.toInjectionSupport() }

        return InternalImplementation(
            after = after,
            before = before
        )
    }

    private inner class InternalImplementation(
        private val before: List<MethodInjectionUtils.MethodInjectionSupport>,
        private val after: List<MethodInjectionUtils.MethodInjectionSupport>,
    ) : HandshakeInterceptor {

        override fun beforeHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            wsAttributes: MutableMap<String, Any>
        ): Boolean {
            val attributes = WebSocketMapAttributesUtils.getOrCreateAttributes(request.attributes)
            WebSocketMapAttributesUtils.setAttributes(wsAttributes, attributes)
            WebSocketMapAttributesUtils.setRequestHeaders(wsAttributes, request.headers.toJavaHeaders())
            WebSocketMapAttributesUtils.setURI(wsAttributes, request.uri)

            before.forEach {
                try {
                    val result = ReflectionUtils.inject(it, request, response, attributes)

                    if (result == false) {
                        return false
                    }
                } catch (e: Exception) {
                    log.warn("Method ${it.method} threw an exception. Please avoid throwing an exception from a @BeforeHandshake function but return false instead.", e)
                    throw e
                }
            }

            return true
        }

        override fun afterHandshake(
            request: ServerHttpRequest,
            response: ServerHttpResponse,
            wsHandler: WebSocketHandler,
            exception: Exception?
        ) {
            val attributes = WebSocketMapAttributesUtils.getOrCreateAttributes(request.attributes)

            after.forEach {
                try {
                    ReflectionUtils.inject(it, request, response, attributes, exception)
                } catch (e: Exception) {
                    log.warn("Method ${it.method} threw an Exception", e)
                }
            }
        }
    }
}