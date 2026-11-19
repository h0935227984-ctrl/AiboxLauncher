package com.aibox.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout

/** 導覽列目前是否固定顯示。桌面與管理頁用它讓出底部區域 */
object NavState {
    @Volatile var visible = false
        private set
    var listener: ((Boolean) -> Unit)? = null
    fun set(v: Boolean) {
        if (visible == v) return
        visible = v
        listener?.invoke(v)
    }
}

/**
 * 全系統自適應寬度高質感導覽列(返回 / 首頁 / 應用程式 / 最近工作)。
 * 常駐固定於螢幕最底部，採用暗色高科技玻璃質感 (Glassmorphism)，包含整合應用程式庫按鈕。
 */
class NavOverlay(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val u = Ui.unit(svc)
    private var bar: View? = null
    private var barParams: WindowManager.LayoutParams? = null

    private fun pu(v: Float) = (v * u).toInt()
    private fun params(w: Int, hgt: Int, flags: Int) = WindowManager.LayoutParams(
        w, hgt, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT
    )

    fun start() { refresh() }

    fun touchDown() {}
    fun touchUp() {}

    /** 設定開關改變時呼叫 */
    fun refresh() {
        if (Store.navBarOn(svc)) {
            showFixedBar()
        } else {
            removeBarNow()
        }
    }

    fun stop() {
        removeBarNow()
    }

    /** 建立並固定顯示於螢幕最底部的自適應高質感導覽列 */
    private fun showFixedBar() {
        if (bar == null) {
            val v = buildBar()
            val p = params(
                WindowManager.LayoutParams.MATCH_PARENT,
                pu(BAR_H_U),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            ).apply {
                gravity = Gravity.BOTTOM
                y = 0
            }
            try {
                wm.addView(v, p)
                bar = v
                barParams = p
            } catch (_: Exception) {
                return
            }
        }
        bar?.alpha = 1.0f
        NavState.set(true)
    }

    private fun removeBarNow() {
        bar?.let { safeRemove(it) }
        bar = null
        barParams = null
        NavState.set(false)
    }

    private fun safeRemove(v: View) { try { wm.removeView(v) } catch (_: Exception) {} }

    /** 建構暗色賽博高質感導覽列條 (包含返回、首頁、應用程式、最近工作) */
    private fun buildBar(): View {
        val row = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(pu(ROW_HPAD_U), pu(ROW_VPAD_U), pu(ROW_HPAD_U), pu(ROW_VPAD_U))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xD90F172A.toInt()) // 深藍黑半透明高科技玻璃質感
                setStroke(maxOf(1, pu(0.2f)), 0x4038BDF8.toInt()) // 青藍色微光頂部邊框
                cornerRadius = pu(14f).toFloat()
            }
        }

        fun btn(res: Int, onClick: () -> Unit) = ImageView(svc).apply {
            setImageResource(res)
            setColorFilter(0xFFF8FAFC.toInt()) // 高亮純白圖示
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(pu(BTN_HPAD_U), pu(BTN_VPAD_U), pu(BTN_HPAD_U), pu(BTN_VPAD_U))
            layoutParams = LinearLayout.LayoutParams(pu(ICON_U + 2 * BTN_HPAD_U), pu(ICON_U + 2 * BTN_VPAD_U)).apply {
                setMargins(pu(GAP_U), 0, pu(GAP_U), 0)
            }
            setOnClickListener { onClick() }
        }

        // 返回按鈕
        row.addView(btn(R.drawable.ic_nav_back) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) })
        // 首頁按鈕
        row.addView(btn(R.drawable.ic_nav_home) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) })
        // 應用程式按鈕 (直接打卡 App 庫)
        row.addView(btn(R.drawable.ic_nav_apps) {
            try {
                val intent = Intent(svc, AppsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                svc.startActivity(intent)
            } catch (_: Exception) {}
        })
        // 最近工作按鈕
        row.addView(btn(R.drawable.ic_nav_recents) { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) })

        return row
    }

    companion object {
        private const val ICON_U = 6.0f
        private const val BTN_HPAD_U = 3.5f
        private const val BTN_VPAD_U = 1.8f
        private const val ROW_HPAD_U = 1.0f
        private const val ROW_VPAD_U = 0.8f
        private const val GAP_U = 5.0f
        private const val BAR_H_U = ICON_U + 2 * BTN_VPAD_U + 2 * ROW_VPAD_U

        /** 導覽列固定顯示時佔用的底部高度(px) */
        fun reservePx(c: Context): Int = (Ui.unit(c) * (BAR_H_U + 2f)).toInt()
    }
}
