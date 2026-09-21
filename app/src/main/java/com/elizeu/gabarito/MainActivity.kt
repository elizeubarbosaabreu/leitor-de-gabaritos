package com.elizeu.gabarito

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.adtize.vast.AdtizeVast
import com.adtize.vast.AdtizeVastConfig
import com.adtize.vast.AdtizeVastCallback
import com.elizeu.gabarito.ui.HomeFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // Delay ad display to ensure activity is fully ready
        Handler(Looper.getMainLooper()).postDelayed({ showInterstitialAd() }, 1000)
    }

    private fun showInterstitialAd() {
        try {
            AdtizeVast.showInterstitial(this, R.id.container,
                AdtizeVastConfig.Builder()
                    .token("b0886e0697e3a0561eeb608e35c95f68")
                    .build(),
                object : AdtizeVastCallback {
                    override fun onAdClicked(clickUrl: String) { }
                    override fun onVideoCompleted() { }
                    override fun onAdError(reason: String) { }
                }
            )
        } catch (e: Exception) {
            // Ignore ad errors, don't crash the app
        }
    }

    fun navigate(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
