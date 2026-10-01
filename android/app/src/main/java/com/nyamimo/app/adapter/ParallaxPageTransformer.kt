package com.nyamimo.app.adapter

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.nyamimo.app.R
import kotlin.math.abs

class ParallaxPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val ivBg = page.findViewById<ImageView>(R.id.ivHeroParallaxBg)
        val ivObject = page.findViewById<ImageView>(R.id.ivHeroParallaxObject)
        val layoutText = page.findViewById<LinearLayout>(R.id.layoutHeroTextGroup)

        val pageWidth = page.width

        when {
            position < -1 -> { // [-Infinity,-1)
                page.alpha = 0f
            }
            position <= 1 -> { // [-1,1]
                page.alpha = 1f

                // 1. Background moves slower to simulate vast distant scenery (Parallax depth)
                ivBg?.translationX = -position * (pageWidth * 0.45f)

                // 2. Foreground Character Cut-out moves with pop-out 3D effect
                ivObject?.let {
                    it.translationX = position * (pageWidth * 0.30f)
                    val scaleFactor = 1f - (abs(position) * 0.12f)
                    it.scaleX = scaleFactor
                    it.scaleY = scaleFactor
                    it.alpha = 1f - (abs(position) * 0.3f)
                }

                // 3. Text fade & slight translation
                layoutText?.let {
                    it.translationX = -position * (pageWidth * 0.15f)
                    it.alpha = 1f - (abs(position) * 0.4f)
                }
            }
            else -> { // (1,+Infinity]
                page.alpha = 0f
            }
        }
    }
}
