package com.elizeu.gabarito

import android.os.Bundle
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
        showInterstitialAd()
    }

    private fun showInterstitialAd() {
        AdtizeVast.showInterstitial(this, R.id.container,
            AdtizeVastConfig.Builder()
                .token("b0886e0697e3a0561eeb608e35c95f68")
                .build(),
            object : AdtizeVastCallback {
                override fun onAdClicked(clickUrl: String) {
                    // primeiro clique no X — anúncio aberto
                }
                override fun onVideoCompleted() { }
                override fun onAdError(reason: String) { }
            }
        )
    }

    fun navigate(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }
}
