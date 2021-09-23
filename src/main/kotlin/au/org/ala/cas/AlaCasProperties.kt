package au.org.ala.cas

import org.apereo.cas.configuration.model.core.authentication.PasswordEncoderProperties
import org.apereo.cas.configuration.model.support.cookie.CookieProperties
import org.apereo.cas.configuration.model.support.cookie.TicketGrantingCookieProperties
import org.apereo.cas.configuration.model.support.jpa.AbstractJpaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(value = "ala")
open class AlaCasProperties(
    @field:NestedConfigurationProperty var cookie: AlaAuthCookieProperties = AlaAuthCookieProperties(),
    @field:NestedConfigurationProperty var userCreator: UserCreatorProperties = UserCreatorProperties(),
    @field:NestedConfigurationProperty val skin: SkinProperties = SkinProperties()
) {
    lateinit var userDetailsBaseUrl: String

    var clientSortOrder: List<String> = listOf("aaf", "Google", "Facebook", "Twitter")
}

open class UserCreatorProperties(
    @field:NestedConfigurationProperty var passwordEncoder: PasswordEncoderProperties = PasswordEncoderProperties(),
    @field:NestedConfigurationProperty var jdbc: JDBCUserCreatorProperties = JDBCUserCreatorProperties(),
    var userCreatePassword: String = "",
    var defaultCountry: String = "AU"
) {
    lateinit var countriesListUrl: String
    lateinit var statesListUrl: String
    var enableUserSurvey: Boolean = false
}

open class JDBCUserCreatorProperties : AbstractJpaProperties() {
    var createUserProcedure: String = "sp_create_user"

    var enableUpdateLastLoginTime: Boolean = true
    var updateLastLoginTimeSql: String = "UPDATE `users` SET `last_login` = CURRENT_TIMESTAMP WHERE `userid` = ?"

    var enableRequestExtraAttributes: Boolean = true
    var countExtraAttributeSql: String = "SELECT count(*) FROM profiles WHERE userid = :userid AND property = :name;"
    var insertExtraAttributeSql: String = "INSERT INTO profiles (userid, property, value) VALUES (:userid, :name, :value);"
    var updateExtraAttributeSql: String = "UPDATE profiles SET value = :value WHERE userid = :userid AND property = :name;"

    var enableUpdateLegacyPasswords: Boolean = true
    var updatePasswordSqls: List<String> = listOf(
        "DELETE FROM passwords WHERE userid = :userid",
        "INSERT INTO passwords (userid, password, type, status) VALUES (:userid, :password, 'bcrypt', 'CURRENT')"
    )
}

open class SkinProperties {
    lateinit var baseUrl: String
    lateinit var termsUrl: String
    lateinit var headerFooterUrl: String
    lateinit var favIconBaseUrl: String
    lateinit var bieBaseUrl: String
    lateinit var bieSearchPath: String
    lateinit var userDetailsUrl: String
    lateinit var resetPasswordUrl: String
    lateinit var createAccountUrl: String
    lateinit var orgShortName: String
    lateinit var orgLongName: String
    lateinit var orgNameKey: String
    lateinit var loginLogo: String
    var cacheDuration: String = "PT30m"
    var uiVersion: Int = 2
}

open class AlaAuthCookieProperties : CookieProperties() {
    init {
        name = "ALA-Auth"
    }

    var rememberMeMaxAge: String = "P14D"
    var quoteValue: Boolean = true
    var urlEncodeValue: Boolean = false
}