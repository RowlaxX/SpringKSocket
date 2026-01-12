package fr.rowlaxx.springksocket.service.aop

import fr.rowlaxx.springksocket.annotation.WebSocketClient
import fr.rowlaxx.springksocket.annotation.WebSocketHandlerProperties
import fr.rowlaxx.springksocket.annotation.WebSocketServer
import fr.rowlaxx.springksocket.model.WebSocketDeserializer
import fr.rowlaxx.springksocket.model.WebSocketSerializer
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

@Service
class WebSocketSerializerDeserializerExtractor(
    private val applicationContext: ApplicationContext
) {

    fun <T : WebSocketSerializer> getSerializer(type: KClass<T>): T {
        if (type == WebSocketSerializer.Null::class) {
            @Suppress("UNCHECKED_CAST")
            return WebSocketSerializer.Null as T
        }
        else if (type == WebSocketSerializer.Passthrough::class) {
            @Suppress("UNCHECKED_CAST")
            return WebSocketSerializer.Passthrough as T
        }

        return applicationContext.getBean(type.java)
    }


    fun <T : WebSocketDeserializer> getDeserializer(type: KClass<T>): T {
        if (type == WebSocketDeserializer.Null::class) {
            @Suppress("UNCHECKED_CAST")
            return WebSocketDeserializer.Null as T
        }
        else if (type == WebSocketDeserializer.Passthrough::class) {
            @Suppress("UNCHECKED_CAST")
            return WebSocketDeserializer.Passthrough as T
        }

        return applicationContext.getBean(type.java)
    }

    private fun extract(
        defaultSerializer: KClass<out WebSocketSerializer>,
        defaultDeserializer: KClass<out WebSocketDeserializer>,
        bean: Any
    ): Pair<WebSocketSerializer, WebSocketDeserializer> {
        val defSer = getSerializer(defaultSerializer)
        val defDes = getDeserializer(defaultDeserializer)

        val type = AopUtils.getTargetClass(bean)
        val anno = type.getAnnotation(WebSocketHandlerProperties::class.java) ?: return defSer to defDes

        return Pair(
            first = if (anno.serializer == WebSocketSerializer.Null::class) defSer else getSerializer(anno.serializer),
            second = if (anno.deserializer == WebSocketDeserializer.Null::class) defDes else getDeserializer(anno.deserializer)
        )
    }

    fun extract(anno: WebSocketClient, bean: Any): Pair<WebSocketSerializer, WebSocketDeserializer> {
        return extract(anno.defaultSerializer, anno.defaultDeserializer, bean)
    }

    fun extract(anno: WebSocketServer, bean: Any): Pair<WebSocketSerializer, WebSocketDeserializer> {
        return extract(anno.defaultSerializer, anno.defaultDeserializer, bean)
    }

}