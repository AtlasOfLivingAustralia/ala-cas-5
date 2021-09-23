package au.org.ala.cas.webflow

import java.io.Serializable

data class Survey @JvmOverloads constructor(
    var affiliation: String = ""
): Serializable