package au.org.ala.cas.events

import au.org.ala.cas.AlaCasProperties
import au.org.ala.cas.jdbc.AlaUserJdbcService
import org.apereo.cas.configuration.support.JpaBeans
import org.apereo.services.persondir.IPersonAttributeDao
import org.apereo.services.persondir.support.CachingPersonAttributeDaoImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(AlaCasProperties::class)
class AlaEventsConfiguration {

    @Autowired
    lateinit var alaCasProperties: AlaCasProperties

    @Bean
    fun lastLoginExecutor(): ExecutorService = Executors.newSingleThreadExecutor()

    @Bean
    @ConditionalOnProperty(prefix="ala.userCreator.jdbc", name=["enableUpdateLastLoginTime"])
    fun alaCasEventListener(
        @Autowired @Qualifier("cachingAttributeRepository") cachingAttributeRepository: IPersonAttributeDao
    ): AlaCasEventListener {
        val dataSource = JpaBeans.newDataSource(alaCasProperties.userCreator.jdbc)
        val jdbcService = AlaUserJdbcService(alaCasProperties, dataSource, DataSourceTransactionManager(dataSource))
        return AlaCasEventListener(jdbcService, lastLoginExecutor(), cachingAttributeRepository)
    }
}
