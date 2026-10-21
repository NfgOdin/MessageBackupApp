package com.messagebackup.app.ui.components

import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.messagebackup.app.R
import com.messagebackup.app.ads.AdManager

/**
 * High-eCPM Native In-Feed Video Ad Card for the Conversation List.
 * Displays muted autoplay video, advertiser brand, headline, and call-to-action button.
 * Automatically falls back to a clean in-feed Banner Ad card if the Native Ad unit fails to load
 * (e.g. when a release ad unit is configured as a Banner unit in AdMob).
 */
@Composable
fun NativeFeedAdCard(
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var useBannerFallback by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        adManager.loadNativeFeedAd(
            context = context,
            onAdLoaded = { ad ->
                nativeAd = ad
                useBannerFallback = false
            },
            onAdFailed = {
                nativeAd = null
                useBannerFallback = true
            }
        )
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val currentAd = nativeAd
    if (currentAd != null) {
        AndroidView(
            factory = { ctx ->
                val inflater = LayoutInflater.from(ctx)
                val adView = inflater.inflate(R.layout.item_native_feed_ad, null) as NativeAdView

                val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
                val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
                val bodyView = adView.findViewById<TextView>(R.id.ad_body)
                val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
                val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
                val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

                adView.headlineView = headlineView
                adView.advertiserView = advertiserView
                adView.bodyView = bodyView
                adView.iconView = iconView
                adView.callToActionView = ctaView
                adView.mediaView = mediaView

                bindNativeAd(adView, currentAd)
                adView
            },
            update = { adView ->
                bindNativeAd(adView, currentAd)
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    } else if (useBannerFallback) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(AdSize.BANNER)
                            adUnitId = ctx.getString(R.string.admob_banner_id)
                            loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }
}

private fun bindNativeAd(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline ?: ""
    (adView.advertiserView as? TextView)?.text = ad.advertiser ?: ad.store ?: "Sponsored"
    (adView.bodyView as? TextView)?.text = ad.body ?: ""

    val icon = ad.icon
    val iconView = adView.iconView as? ImageView
    if (icon != null && iconView != null) {
        iconView.setImageDrawable(icon.drawable)
        iconView.visibility = android.view.View.VISIBLE
    } else {
        iconView?.visibility = android.view.View.GONE
    }

    val cta = ad.callToAction
    val ctaView = adView.callToActionView as? Button
    if (cta != null && ctaView != null) {
        ctaView.text = cta
        ctaView.visibility = android.view.View.VISIBLE
    } else {
        ctaView?.visibility = android.view.View.GONE
    }

    adView.setNativeAd(ad)
}
