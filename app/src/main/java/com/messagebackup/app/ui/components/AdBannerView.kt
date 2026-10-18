package com.messagebackup.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.messagebackup.app.R

/**
 * Modern Jetpack Compose wrapper for Google AdMob Adaptive Banner.
 *
 * @param isAdsRemoved If true, renders nothing.
 * @param modifier Composable modifier.
 */
@Composable
fun AdBannerView(
    isAdsRemoved: Boolean,
    modifier: Modifier = Modifier
) {
    if (isAdsRemoved) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = context.getString(R.string.admob_banner_id)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
