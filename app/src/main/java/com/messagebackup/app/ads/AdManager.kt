package com.messagebackup.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.messagebackup.app.R

/**
 * Manages Google AdMob initialization, UMP GDPR/EEA Consent Flow, and Rewarded Ad preloading.
 */
class AdManager(private val context: Context) {

    companion object {
        private const val TAG = "AdManager"
    }

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isRewardedInterstitialLoading = false
    private var interstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime = 0L

    /**
     * Initializes the User Messaging Platform (UMP) SDK for GDPR/UK compliance,
     * then initializes the Google Mobile Ads SDK upon consent resolution.
     */
    fun initializeUmpAndAds(activity: Activity, onComplete: () -> Unit = {}) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds(activity)
                    }
                    onComplete()
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent request error: ${requestConsentError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(activity)
                }
                onComplete()
            }
        )
    }

    private fun initializeMobileAds(context: Context) {
        MobileAds.initialize(context) {
            loadRewardedAd(context)
            loadRewardedInterstitialAd(context)
            loadInterstitialAd(context)
        }
    }

    /**
     * Preloads a high-eCPM Rewarded Interstitial Ad for high-value actions (Legal PDF export, Desktop viewer).
     */
    fun loadRewardedInterstitialAd(context: Context) {
        if (rewardedInterstitialAd != null || isRewardedInterstitialLoading) return

        isRewardedInterstitialLoading = true
        val adUnitId = context.getString(R.string.admob_rewarded_interstitial_id)
        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    isRewardedInterstitialLoading = false
                    Log.d(TAG, "Rewarded Interstitial Ad successfully loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedInterstitialAd = null
                    isRewardedInterstitialLoading = false
                    Log.w(TAG, "Rewarded Interstitial Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows the premium Rewarded Interstitial Ad.
     * Falls back seamlessly to the standard Rewarded Ad if not yet loaded.
     */
    fun showRewardedInterstitialOrFallback(
        activity: Activity,
        isAdsRemoved: Boolean,
        onUserEarnedReward: () -> Unit
    ) {
        if (isAdsRemoved) {
            onUserEarnedReward()
            return
        }

        val ad = rewardedInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitialAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitialAd(activity)
                    onUserEarnedReward()
                }
            }

            ad.show(activity) {
                onUserEarnedReward()
            }
        } else {
            // Fallback to standard rewarded ad
            loadRewardedInterstitialAd(activity)
            showRewardedAd(activity, isAdsRemoved, onUserEarnedReward)
        }
    }

    /**
     * Preloads a Rewarded Ad so it is ready for instant presentation when user taps Export.
     */
    fun loadRewardedAd(context: Context) {
        if (rewardedAd != null || isAdLoading) return

        isAdLoading = true
        val adUnitId = context.getString(R.string.admob_rewarded_id)
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Rewarded Ad successfully loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isAdLoading = false
                    Log.w(TAG, "Rewarded Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows the preloaded Rewarded Ad.
     * If no ad is ready or ads are removed, immediately triggers the onUserEarnedReward callback.
     */
    fun showRewardedAd(
        activity: Activity,
        isAdsRemoved: Boolean,
        onUserEarnedReward: () -> Unit
    ) {
        if (isAdsRemoved) {
            // User purchased "Remove Ads", bypass ad playback completely
            onUserEarnedReward()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    // Preload the next rewarded ad
                    loadRewardedAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    loadRewardedAd(activity)
                    // If ad display fails, grant export gracefully rather than blocking user
                    onUserEarnedReward()
                }
            }

            ad.show(activity) {
                onUserEarnedReward()
            }
        } else {
            // Ad was not ready; don't penalize user - proceed with export and queue load
            loadRewardedAd(activity)
            onUserEarnedReward()
        }
    }

    /**
     * Preloads an Interstitial Ad for screen transition monetization.
     */
    fun loadInterstitialAd(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return

        isInterstitialLoading = true
        val adUnitId = context.getString(R.string.admob_interstitial_id)
        val adRequest = AdRequest.Builder().build()

        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.d(TAG, "Interstitial Ad successfully loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Shows an Interstitial Ad upon completing actions (e.g., closing conversation viewer).
     * Protected by a 60-second frequency cap to avoid overwhelming users and to keep 5-star ratings.
     */
    fun showInterstitialAd(
        activity: Activity,
        isAdsRemoved: Boolean,
        onComplete: () -> Unit = {}
    ) {
        if (isAdsRemoved) {
            onComplete()
            return
        }

        val now = System.currentTimeMillis()
        val elapsedSinceLast = now - lastInterstitialShownTime

        // Pacing limit: at least 60 seconds between interstitials
        if (elapsedSinceLast < 60_000L) {
            onComplete()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitialAd(activity)
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onComplete()
                }
            }

            ad.show(activity)
        } else {
            loadInterstitialAd(activity)
            onComplete()
        }
    }
}
