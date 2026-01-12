package fr.rowlaxx.springksocket.conf

import fr.rowlaxx.springkutils.concurrent.utils.ExecutorsUtils
import org.springframework.context.annotation.Configuration

@Configuration
class WebSocketConfiguration {

    val executor = ExecutorsUtils.newFailsafeScheduledExecutor(8, "WebSocket")

}