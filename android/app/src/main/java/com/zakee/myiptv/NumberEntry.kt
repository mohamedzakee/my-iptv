package com.zakee.myiptv

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/** Type a channel number on the remote; it jumps after a short pause (like a normal TV). */
class NumberEntry(private val display: TextView, private val onNumber: (Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var digits = ""
    private val commit = Runnable {
        display.visibility = View.GONE
        digits.toIntOrNull()?.let(onNumber)
        digits = ""
    }

    fun handle(keyCode: Int): Boolean {
        val d = when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
            else -> return false
        }
        if (digits.length >= 3) digits = ""
        digits += d
        display.text = digits
        display.visibility = View.VISIBLE
        handler.removeCallbacks(commit)
        handler.postDelayed(commit, 1500)
        return true
    }

    fun cancel() = handler.removeCallbacks(commit)
}
