package au.org.ala.cas.jndi

import au.org.ala.utils.logger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariJNDIFactory
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory

/**
 * This Configuration simply creates simple JNDI datasources based off the JndiConfigurationProperites.
 */
@Configuration
@EnableConfigurationProperties(JndiConfigurationProperties::class)
class ServletContextConfig {

    companion object {
        val log = logger<ServletContextConfig>()
    }

    @Autowired
    lateinit var jndiConfigurationProperties: JndiConfigurationProperties

    @Bean
    fun tomcatEmbeddedServletContainerFactory(): TomcatEmbeddedServletContainerFactory {
        return object : TomcatEmbeddedServletContainerFactory() {

            override fun getTomcatEmbeddedServletContainer(
                    tomcat: Tomcat): TomcatEmbeddedServletContainer {
                tomcat.enableNaming()
                return super.getTomcatEmbeddedServletContainer(tomcat)
            }

            override fun postProcessContext(context: Context) {

                jndiConfigurationProperties.hikari.map { config ->
                    ContextResource().apply {
                        name = config.name
                        type = DataSource::class.java.name
                        setProperty("factory", HikariJNDIFactory::class.java.name)
                        config.driverClass?.let { setProperty("driverClassName", it) }
                        setProperty("jdbcUrl", config.url)
                        setProperty("url", config.url)
                        setProperty("username", config.user)
                        setProperty("password", config.password)
                        config.allowPoolSuspension?.let { setProperty("allowPoolSuspension", it) }
                        config.autoCommit?.let { setProperty("autoCommit", it) }
                        config.catalog?.let { setProperty("catalog", it) }
                        config.connectionInitSql?.let { setProperty("connectionInitSql", it) }
                        config.connectionTestQuery?.let { setProperty("connectionTestQuery", it) }
                        config.connectionTimeout?.let { setProperty("connectionTimeout", it) }
                        config.idleTimeout?.let { setProperty("idleTimeout", it) }
                        config.initializationFailTimeout?.let { setProperty("initializationFailTimeout", it) }
                        config.isolateInternalQueries?.let { setProperty("isolateInternalQueries", it) }
                        config.leakDetectionThreshold?.let { setProperty("leakDetectionThreshold", it) }
                        config.maximumPoolSize?.let { setProperty("maximumPoolSize", it) }
                        config.maxLifetime?.let { setProperty("maxLifetime", it) }
                        config.minimumIdle?.let { setProperty("minimumIdle", it) }
                        config.poolName?.let { setProperty("poolName", it) }
                        config.readOnly?.let { setProperty("readOnly", it) }
                        config.registerMbeans?.let { setProperty("registerMbeans", it) }
                        config.schema?.let { setProperty("schema", it) }
                        config.transactionIsolation?.let { setProperty("transactionIsolation", it) }
                        config.validationTimeout?.let { setProperty("validationTimeout", it) }
                        config.dataSourceProperties.forEach { (name, value) -> setProperty("dataSource.$name", value) }
                    }
                }.forEach(context.namingResources::addResource)
            }
        }
    }

}
