package com.heuge.busapp.utils

import android.graphics.Color
import android.os.Looper
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View


fun View.enableEInkFeedback() {
    val handler = Handler(Looper.getMainLooper())
    val originalBackground = background

    setOnTouchListener(fun(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.setBackgroundColor(Color.BLACK) // full black flash
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                handler.postDelayed({
                    v.background = originalBackground // restore after short delay
                }, 80) // tweak timing
            }
        }
        return false // keep normal click events working
    })
}



