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
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.AdLoader
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

    @Volatile
    private var isMobileAdsInitialized = false
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
        val appContext = this.context.applicationContext
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
                        initializeMobileAds(appContext)
                    }
                    onComplete()
                }
            },
            { requestConsentError ->
                Log.w(TAG, "Consent request error: ${requestConsentError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(appContext)
                }
                onComplete()
            }
        )
    }

    /**
     * Checks if GDPR/EEA Privacy Options form is required to be surfaced in Settings.
     */
    fun isPrivacyOptionsRequired(activity: Activity): Boolean {
        return try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            consentInformation.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Displays the UMP Privacy Options / Consent Form so users in EEA/UK can modify their ad choices.
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismiss: (formError: String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
                onDismiss(formError.message)
            } else {
                onDismiss(null)
            }
        }
    }

    private fun initializeMobileAds(context: Context) {
        val appCtx = context.applicationContext
        if (isMobileAdsInitialized) {
            loadRewardedAd(appCtx)
            loadRewardedInterstitialAd(appCtx)
            loadInterstitialAd(appCtx)
            return
        }
        isMobileAdsInitialized = true
        MobileAds.initialize(appCtx) {
            loadRewardedAd(appCtx)
            loadRewardedInterstitialAd(appCtx)
            loadInterstitialAd(appCtx)
        }
    }

    /**
     * Preloads a high-eCPM Rewarded Interstitial Ad for high-value actions (Legal PDF export, Desktop viewer).
     */
    fun loadRewardedInterstitialAd(context: Context = this.context) {
        if (rewardedInterstitialAd != null || isRewardedInterstitialLoading) return

        val appCtx = context.applicationContext
        isRewardedInterstitialLoading = true
        val adUnitId = appCtx.getString(R.string.admob_rewarded_interstitial_id)
        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            appCtx,
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

        val appCtx = this.context.applicationContext
        val ad = rewardedInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitialAd(appCtx)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedInterstitialAd = null
                    loadRewardedInterstitialAd(appCtx)
                    onUserEarnedReward()
                }
            }

            ad.show(activity) {
                onUserEarnedReward()
            }
        } else {
            // Fallback to standard rewarded ad
            loadRewardedInterstitialAd(appCtx)
            showRewardedAd(
                activity = activity,
                isAdsRemoved = isAdsRemoved,
                onUserEarnedReward = onUserEarnedReward
            )
        }
    }

    /**
     * Preloads a Rewarded Ad so it is ready for instant presentation when user taps Export.
     */
    fun loadRewardedAd(context: Context = this.context) {
        if (rewardedAd != null || isAdLoading) return

        val appCtx = context.applicationContext
        isAdLoading = true
        val adUnitId = appCtx.getString(R.string.admob_rewarded_id)
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            appCtx,
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
     * When requireStrictReward is true (e.g., earning runes in the Arcade), rewards are ONLY granted
     * when the user actually watches and completes the ad.
     */
    fun showRewardedAd(
        activity: Activity,
        isAdsRemoved: Boolean,
        requireStrictReward: Boolean = false,
        onAdUnavailable: () -> Unit = {},
        onUserEarnedReward: () -> Unit
    ) {
        if (isAdsRemoved) {
            // User purchased "Remove Ads", bypass ad playback completely
            onUserEarnedReward()
            return
        }

        val appCtx = this.context.applicationContext
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    // Preload the next rewarded ad
                    loadRewardedAd(appCtx)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    loadRewardedAd(appCtx)
                    if (requireStrictReward) {
                        onAdUnavailable()
                    } else {
                        // If ad display fails for a utility export, grant export gracefully rather than blocking user
                        onUserEarnedReward()
                    }
                }
            }

            ad.show(activity) {
                onUserEarnedReward()
            }
        } else {
            loadRewardedAd(appCtx)
            if (requireStrictReward) {
                onAdUnavailable()
            } else {
                // Ad was not ready; don't penalize user for utility export - proceed and queue load
                onUserEarnedReward()
            }
        }
    }

    /**
     * Preloads an Interstitial Ad for screen transition monetization.
     */
    fun loadInterstitialAd(context: Context = this.context) {
        if (interstitialAd != null || isInterstitialLoading) return

        val appCtx = context.applicationContext
        isInterstitialLoading = true
        val adUnitId = appCtx.getString(R.string.admob_interstitial_id)
        val adRequest = AdRequest.Builder().build()

        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            appCtx,
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

        val appCtx = this.context.applicationContext
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitialAd(appCtx)
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitialAd(appCtx)
                    onComplete()
                }
            }

            ad.show(activity)
        } else {
            loadInterstitialAd(appCtx)
            onComplete()
        }
    }

    /**
     * Loads a high-eCPM Native In-Feed Video Ad with muted autoplay.
     */
    fun loadNativeFeedAd(
        context: Context,
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailed: () -> Unit = {}
    ) {
        val adUnitId = context.getString(R.string.admob_native_id)
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true) // Always start video muted so user is never startled
            .build()

        val adOptions = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native In-Feed Video Ad loaded successfully.")
                onAdLoaded(nativeAd)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Native In-Feed Ad failed to load: ${loadAdError.message}")
                    onAdFailed()
                }
            })
            .withNativeAdOptions(adOptions)
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}
