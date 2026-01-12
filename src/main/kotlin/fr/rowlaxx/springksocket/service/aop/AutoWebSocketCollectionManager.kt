package fr.rowlaxx.springksocket.service.aop

import fr.rowlaxx.springksocket.model.WebSocket
import fr.rowlaxx.springksocket.core.AutoWebSocketCollection
import fr.rowlaxx.springkutils.reflection.utils.ReflectionUtils
import org.springframework.stereotype.Service
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@Service
class AutoWebSocketCollectionManager {
    private val map = ConcurrentHashMap<KClass<*>, List<AutoWebSocketCollection>>()

    private fun find(bean: Any): List<AutoWebSocketCollection> {
        val result = mutableListOf<AutoWebSocketCollection>()

        ReflectionUtils.findFieldsWithType(bean, AutoWebSocketCollection::class.java).onEach {
            if (Modifier.isFinal(it.modifiers)) {
                it.isAccessible = true
                result.add(it.get(bean) as AutoWebSocketCollection)
            }
            else {
                throw IllegalArgumentException("Please make field '${it.name}' in class ${bean.javaClass.simpleName} immutable")
            }
        }

        return result
    }

    fun initializeIfNotDone(bean: Any) {
        map.computeIfAbsent(bean::class) { find(bean) }
    }

    fun onAvailable(bean: Any, webSocket: WebSocket) {
        map[bean::class]!!.forEach { it.add(webSocket) }
    }

    fun onUnavailable(bean: Any, webSocket: WebSocket) {
        map[bean::class]!!.forEach { it.remove(webSocket) }
    }

}