package com.github.xckevin927.android.battery.widget.activity

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.animation.keyframe.BaseKeyframeAnimation.AnimationListener
import com.github.xckevin927.android.battery.widget.R

class AnimActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anim)
        val lottieView:LottieAnimationView = findViewById(R.id.lottie_view)
        lottieView.apply {
            setAnimation(R.raw.start_anim)
            repeatCount = 1
            repeatMode = LottieDrawable.REVERSE
            addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                }

                override fun onAnimationEnd(animation: Animator) {
                    startActivity(Intent(this@AnimActivity, TabActivity::class.java))
                    finish()
                }

                override fun onAnimationCancel(animation: Animator) {
                }

                override fun onAnimationRepeat(animation: Animator) {
                }
            })
            playAnimation()

        }
    }
}