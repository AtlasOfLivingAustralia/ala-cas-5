package au.org.ala.cas.thymeleaf

import org.thymeleaf.dialect.AbstractProcessorDialect
import org.thymeleaf.processor.IProcessor

class AlaDialect(val alaTemplateClient: AlaTemplateClient) : AbstractProcessorDialect("ALA Dialect", "ala", 10) {

    override fun getProcessors(dialectPrefix: String): MutableSet<IProcessor> {
        return mutableSetOf(
            AlaHeaderFooterTagProcessor(dialectPrefix, "banner", alaTemplateClient),
            AlaHeaderFooterTagProcessor(dialectPrefix, "footer", alaTemplateClient),
            AlaHeaderFooterTagProcessor(dialectPrefix, "head", alaTemplateClient)
        )
    }

}

