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
}
