package com.voiceink.android.data.subscription

import android.app.Activity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Subscription tier levels
 */
enum class SubscriptionTier {
    FREE,
    PRO
}

/**
 * Subscription status with details
 */
data class SubscriptionStatus(
    val tier: SubscriptionTier,
    val expirationDate: Long? = null, // Timestamp when subscription expires
    val isTrialActive: Boolean = false
)

/**
 * Repository for managing subscription status
 *
 * Currently a stub that always returns FREE tier.
 * Will be integrated with RevenueCat later for actual billing.
 *
 * TODO: Integrate with RevenueCat
 * - Add dependency: implementation("com.revenuecat.purchases:purchases:7.+")
 * - Initialize Purchases SDK in Application.onCreate()
 * - Implement purchase() and restorePurchases() methods
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _subscriptionStatus = MutableStateFlow(
        SubscriptionStatus(tier = SubscriptionTier.FREE)
    )
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus.asStateFlow()

    /**
     * Current subscription tier
     */
    val currentTier: StateFlow<SubscriptionTier> = MutableStateFlow(SubscriptionTier.FREE)

    /**
     * Quick check if user has pro subscription
     */
    val isPro: Boolean
        get() = _subscriptionStatus.value.tier == SubscriptionTier.PRO

    /**
     * Initialize subscription status
     * Should be called on app start to sync with RevenueCat
     */
    suspend fun initialize() {
        // TODO: Initialize RevenueCat and fetch customer info
        // Purchases.sharedInstance.getCustomerInfo { ... }
    }

    /**
     * Purchase pro subscription
     * @param activity Activity context for launching purchase flow
     * @return Result with success or error message
     */
    suspend fun purchase(activity: Activity): Result<Unit> {
        // TODO: Implement RevenueCat purchase flow
        // Purchases.sharedInstance.purchaseWith(...)
        return Result.failure(NotImplementedError("RevenueCat integration pending"))
    }

    /**
     * Restore previous purchases
     * @return Result with success or error message
     */
    suspend fun restorePurchases(): Result<Unit> {
        // TODO: Implement RevenueCat restore
        // Purchases.sharedInstance.restorePurchases { ... }
        return Result.failure(NotImplementedError("RevenueCat integration pending"))
    }

    /**
     * Set subscription tier (for testing or manual override)
     */
    fun setTier(tier: SubscriptionTier) {
        _subscriptionStatus.value = _subscriptionStatus.value.copy(tier = tier)
    }

    /**
     * Check if a specific feature is available for current tier
     */
    fun hasFeature(feature: ProFeature): Boolean {
        return when (_subscriptionStatus.value.tier) {
            SubscriptionTier.PRO -> true // Pro has all features
            SubscriptionTier.FREE -> feature.availableInFree
        }
    }
}

/**
 * Features that may be restricted by tier
 */
enum class ProFeature(val availableInFree: Boolean) {
    UNLIMITED_LOCAL_TRANSCRIPTION(false),
    UNLIMITED_CLOUD_TRANSCRIPTION(false),
    REAL_TIME_STREAMING(false),
    AUTO_PUNCTUATION(false),
    NO_ADS(false),
    TRANSCRIPTION_HISTORY(true), // History is free for everyone
    EXPORT_HISTORY(false)
}
