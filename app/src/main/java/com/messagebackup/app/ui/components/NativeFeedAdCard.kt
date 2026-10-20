package com.messagebackup.app.ui.components

import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.messagebackup.app.R
import com.messagebackup.app.ads.AdManager

/**
 * High-eCPM Native In-Feed Video Ad Card for the Conversation List.
 * Displays muted autoplay video, advertiser brand, headline, and call-to-action button.
 */
@Composable
fun NativeFeedAdCard(
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        adManager.loadNativeFeedAd(
            context = context,
            onAdLoaded = { ad -> nativeAd = ad },
            onAdFailed = { nativeAd = null }
        )
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val currentAd = nativeAd ?: return

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
