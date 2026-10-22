package com.messagebackup.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.messagebackup.app.R

/**
 * Modern Jetpack Compose wrapper for Google AdMob Adaptive Banner.
 * Positioned above the system navigation bar via navigationBarsPadding.
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

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = context.getString(R.string.admob_banner_id)
                        loadAd(AdRequest.Builder().build())
                    }
                },
                onRelease = { adView ->
                    adView.destroy()
                }
            )
        }
    }
}
