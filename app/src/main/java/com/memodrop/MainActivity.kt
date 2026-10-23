package com.memodrop

import android.media.MediaPlayer
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private lateinit var colorBar: LinearLayout
    private val penColors = intArrayOf(Color.BLACK, 0xFFD32F2F.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6D4C41.toInt())
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
        colorBar = findViewById(R.id.color_bar)
        buildColorBar()
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
        colorBar.visibility = if (removed) View.VISIBLE else View.GONE
        canvas.penColor = if (removed) getSharedPreferences("MemoDrop", MODE_PRIVATE).getInt("pen_color", Color.BLACK) else Color.BLACK
        updateColorSelection()
        if (removed) {
            adView?.destroy()
            adContainer.removeAllViews()
            adView = null
        } else if (adView == null) {
            MobileAds.initialize(this)
            adView = AdView(this).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()))
                adUnitId = getString(R.string.admob_banner_id)
                adContainer.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun buildColorBar() {
        val d = resources.displayMetrics.density
        penColors.forEach { color ->
            val dot = View(this).apply {
                tag = color
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
                layoutParams = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt()).apply { marginStart = (12 * d).toInt(); marginEnd = (12 * d).toInt() }
                setOnClickListener {
                    canvas.penColor = color
                    getSharedPreferences("MemoDrop", MODE_PRIVATE).edit().putInt("pen_color", color).apply()
                    updateColorSelection()
                }
            }
            colorBar.addView(dot)
        }
    }

    private fun updateColorSelection() {
        for (i in 0 until colorBar.childCount) {
            val dot = colorBar.getChildAt(i)
            val selected = dot.tag == canvas.penColor
            dot.scaleX = if (selected) 1.3f else 1f
            dot.scaleY = dot.scaleX
            dot.alpha = if (selected) 1f else 0.6f
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
