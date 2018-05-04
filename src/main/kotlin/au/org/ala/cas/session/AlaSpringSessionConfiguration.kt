package au.org.ala.cas.session

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.session.data.redis.config.ConfigureRedisAction

// TODO Remove for CAS 5.3.0+ because Pac4j WebFlow actions will no longer use http session
@Configuration
@Import(RedisAutoConfiguration::class) // Need to import manually because @SpringBootApplication on CasWebApplication::class excludes it
class AlaSpringSessionConfiguration {

    @ConditionalOnProperty("spring.session.disable-redis-config-action")
    @Bean
    fun configureRedisAction(): ConfigureRedisAction = ConfigureRedisAction.NO_OP
}