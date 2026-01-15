package com.voiceink.android.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.voiceink.android.data.subscription.SubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling AdMob advertisements.
 *
 * Ad placement strategy (non-intrusive for free users):
 * - Banner ad at bottom of HomeScreen (always visible for free)
 * - Interstitial ad after every 5th transcription
 *
 * Pro users see no ads.
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val subscriptionRepository: SubscriptionRepository
) {
    companion object {
        private const val TAG = "AdManager"

        // Test ad unit IDs - replace with real IDs before production release
        // See: https://developers.google.com/admob/android/test-ads
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

        // TODO: Replace with real ad unit IDs
        private const val BANNER_AD_UNIT_ID = TEST_BANNER_AD_UNIT_ID
        private const val INTERSTITIAL_AD_UNIT_ID = TEST_INTERSTITIAL_AD_UNIT_ID

        // Show interstitial every N transcriptions
        private const val INTERSTITIAL_FREQUENCY = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var interstitialAd: InterstitialAd? = null
    private var transcriptionCount = 0
    private var isInitialized = false

    /**
     * Initialize the Mobile Ads SDK.
     * Should be called once when the app starts.
     */
    fun initialize() {
        if (isInitialized) return

        scope.launch {
            try {
                MobileAds.initialize(context) { initializationStatus ->
                    Log.d(TAG, "AdMob initialized: $initializationStatus")
                    isInitialized = true
                    // Preload first interstitial
                    if (!subscriptionRepository.isPro) {
                        loadInterstitialAd()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AdMob", e)
            }
        }
    }

    /**
     * Check if banner ads should be shown.
     * Returns false for Pro users.
     */
    val shouldShowBannerAd: Boolean
        get() = !subscriptionRepository.isPro

    /**
     * Get the banner ad unit ID.
     */
    val bannerAdUnitId: String
        get() = BANNER_AD_UNIT_ID

    /**
     * Called after each successful transcription.
     * Shows interstitial ad every N transcriptions for free users.
     *
     * @param activity Activity context for showing the ad
     */
    fun onTranscriptionComplete(activity: Activity) {
        if (subscriptionRepository.isPro) return

        transcriptionCount++

        if (transcriptionCount >= INTERSTITIAL_FREQUENCY) {
            showInterstitialAd(activity)
            transcriptionCount = 0
        }
    }

    /**
     * Load an interstitial ad for later display.
     */
    private fun loadInterstitialAd() {
        if (subscriptionRepository.isPro) return

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    setupInterstitialCallbacks()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Show the interstitial ad if loaded.
     */
    private fun showInterstitialAd(activity: Activity) {
        val ad = interstitialAd
        if (ad != null) {
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad not ready")
            // Try to load one for next time
            loadInterstitialAd()
        }
    }

    /**
     * Setup callbacks for interstitial ad events.
     */
    private fun setupInterstitialCallbacks() {
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                // Load next ad
                loadInterstitialAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                interstitialAd = null
                loadInterstitialAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad shown")
            }
        }
    }

    /**
     * Reset the transcription counter (useful after purchase).
     */
    fun resetCounter() {
        transcriptionCount = 0
        interstitialAd = null
    }
}
