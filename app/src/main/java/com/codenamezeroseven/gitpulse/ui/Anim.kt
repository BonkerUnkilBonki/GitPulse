package com.codenamezeroseven.gitpulse.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/** Shared motion helpers - one consistent, springy feel across screens. */
object Anim {

    private val decel = DecelerateInterpolator(1.1f)
    private val overshoot = OvershootInterpolator(1.15f)

    /** Animated count-up for stat values. Skips when the value didn't change. */
    fun countUp(tv: TextView, target: Int, duration: Long = 850, suffix: String = "") {
        val tag = tv.tag
        if (tag is Int && tag == target) {
            tv.text = target.toString() + suffix
            return
        }
        tv.tag = target
        val start = (tag as? Int) ?: 0
        ValueAnimator.ofInt(start, target).apply {
            setDuration(duration)
            interpolator = decel
            addUpdateListener { tv.text = it.animatedValue.toString() + suffix }
            start()
        }
    }

    /** Staggered slide-up + fade entrance for a group of views. */
    fun staggerIn(vararg views: View, gap: Long = 55, duration: Long = 340) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = 26f * v.resources.displayMetrics.density
            v.animate().alpha(1f).translationY(0f)
                .setDuration(duration)
                .setStartDelay(i * gap)
                .setInterpolator(decel)
                .start()
        }
    }

    /** Stagger the direct children of a container (e.g. the cards of a screen). */
    fun staggerChildren(container: ViewGroup, gap: Long = 55) {
        val kids = (0 until container.childCount).map { container.getChildAt(it) }
        staggerIn(*kids.toTypedArray(), gap = gap)
    }

    /** Springy scale + fade pop - used for highlights. */
    fun popIn(view: View, delay: Long = 0, scaleFrom: Float = 0.85f) {
        view.alpha = 0f
        view.scaleX = scaleFrom
        view.scaleY = scaleFrom
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420)
            .setStartDelay(delay)
            .setInterpolator(overshoot)
            .start()
    }

    /** RecyclerView row entrance with position-based stagger. */
    fun listIn(view: View, position: Int) {
        view.alpha = 0f
        view.translationY = 22f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f)
            .setDuration(280)
            .setStartDelay(position.coerceAtMost(8) * 34L)
            .setInterpolator(decel)
            .start()
    }

    /** Subtle press-down scale feedback; springs back on release. */
    fun pressable(vararg views: View) {
        for (v in views) {
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(0.95f).scaleY(0.95f)
                        .setDuration(90).setInterpolator(decel).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(260).setInterpolator(overshoot).start()
                }
                false // don't consume - normal clicks still work
            }
        }
    }

    /** Gentle breathing loop for empty states. Starts only once per view. */
    fun breathe(view: View) {
        if (view.getTag(view.id) == true) return
        view.setTag(view.id, true)
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.55f).apply {
            duration = 1400
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }
}
