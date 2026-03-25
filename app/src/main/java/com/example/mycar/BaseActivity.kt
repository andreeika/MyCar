package com.example.mycar

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

open class BaseActivity : AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        applyPressAnimationsToAll(window.decorView.rootView)
    }

    private fun applyPressAnimationsToAll(view: View) {
        when {
            // Поля ввода — не трогаем, иначе блокируется фокус и клавиатура
            view is EditText -> return
            view is TextInputEditText -> return
            view is TextInputLayout -> return
            view is Button -> view.applyPressAnimation(scale = 0.95f)
            view is MaterialCardView && view.isClickable -> view.applyPressAnimation(scale = 0.97f)
            view is CardView && view.isClickable -> view.applyPressAnimation(scale = 0.97f)
            view is ImageView && view.isClickable -> view.applyPressAnimation(scale = 0.88f)
            view is android.widget.LinearLayout && view.isClickable -> view.applyPressAnimation(scale = 0.96f)
            view is TextView && view.isClickable -> view.applyPressAnimation(scale = 0.95f)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyPressAnimationsToAll(view.getChildAt(i))
            }
        }
    }
}

fun View.applyPressAnimation(scale: Float = 0.93f, duration: Long = 100) {
    setOnTouchListener { v, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                v.animate()
                    .scaleX(scale).scaleY(scale)
                    .setDuration(duration)
                    .start()
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(duration)
                    .start()
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    v.performClick()
                }
            }
        }
        true
    }
}
