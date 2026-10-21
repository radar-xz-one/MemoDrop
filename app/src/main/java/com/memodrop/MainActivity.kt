package com.memodrop

import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    private lateinit var canvas: HandwritingCanvas
    private var tearSound: MediaPlayer? = null
    private lateinit var billing: BillingManager
    private lateinit var adContainer: FrameLayout
    private lateinit var removeAds: TextView
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        canvas = findViewById(R.id.handwriting_canvas)
        tearSound = MediaPlayer.create(this, R.raw.tear_sound)
        canvas.onTearStartListener = ::playTearSound

        adContainer = findViewById(R.id.ad_banner_container)
        removeAds = findViewById(R.id.remove_ads)
        billing = BillingManager(this, ::applyAdsRemoved) { price ->
            if (price != null) removeAds.text = getString(R.string.remove_ads, price)
        }
        removeAds.setOnClickListener { billing.purchase() }
        applyAdsRemoved(BillingManager.isAdsRemoved(this))
        billing.start()

        canvas.loadFromStorage()
        if (!canvas.hasMemo()) canvas.startIntro()
    }

    private fun applyAdsRemoved(removed: Boolean) {
        val vis = if (removed) View.GONE else View.VISIBLE
        adContainer.visibility = vis
        removeAds.visibility = vis
        if (removed) {
            adView?.destroy()
            adContainer.removeAllViews()
            adView = null
        } else if (adView == null) {
            MobileAds.initialize(this)
            adView = AdView(this).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = getString(R.string.admob_banner_id)
                adContainer.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun playTearSound() {
        tearSound?.let { if (it.isPlaying) it.seekTo(0) else it.start() }
    }

    override fun onDestroy() {
        super.onDestroy()
        tearSound?.release()
        adView?.destroy()
        billing.end()
    }
}
