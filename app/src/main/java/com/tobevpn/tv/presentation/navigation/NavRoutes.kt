package com.tobevpn.tv.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
object MainRoute

@Serializable
object ServerListRoute

@Serializable
object SettingsRoute

@Serializable
object AppFilterRoute

@Serializable
object DevicesRoute

@Serializable
object ReferralsRoute

@Serializable
object PromocodesRoute

@Serializable
object AboutRoute

@Serializable
object SupportRoute

@Serializable
object StatsRoute

@Serializable
object SpeedTestRoute

@Serializable
object SubscriptionRoute

@Serializable
object OnboardingRoute

@Serializable
object MobileAppInstallRoute

/**
 * How the user chose to sign in on the install screen. The value travels as a
 * plain string because type-safe navigation has no built-in NavType for enums.
 */
enum class PairingEntry {
    /** ToBeVPN is installed on an Android phone — pair with a device code. */
    MOBILE_APP,

    /** iPhone, where the app is not published yet — sign in through Telegram. */
    IPHONE,

    /** No phone with the app at all — sign in through Telegram. */
    NO_PHONE,
    ;

    companion object {
        fun fromRoute(value: String): PairingEntry =
            entries.firstOrNull { it.name == value } ?: MOBILE_APP
    }
}

@Serializable
data class PairingRoute(val entry: String = PairingEntry.MOBILE_APP.name)
