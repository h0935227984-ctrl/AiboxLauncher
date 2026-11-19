package com.aibox.launcher

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HomeActivity : BaseActivity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var clockPanel: ClockPanel
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var box: GridLayout
    private lateinit var btnMore: ImageView
    private lateinit var scrollView: View
    private lateinit var bgImage: ImageView
    private lateinit var bgVideo: TextureView
    private lateinit var scrim: View
    private lateinit var video: VideoBg

    // 系統列內距 + 導覽列出現時額外預留的高度
    private var insTop = 0
    private var insBottom = 0
    private var navExtra = 0
    private var navAnim: ValueAnimator? = null

    // 桌布圖片快取 / 虛化
    private var loadedKey: String? = null
    private var blurVersion = 0
    @Volatile private var smallKey: String? = null
    @Volatile private var small: Bitmap? = null
    private val blurExec = Executors.newSingleThreadExecutor()

    private val tick = object : Runnable {
        override fun run() { updateClock(); h.postDelayed(this, 5000) }
    }

    private val multiPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "處理多張桌布中…", Toast.LENGTH_SHORT).show()
            Wallpaper.importUris(this, uris) { ok ->
                Toast.makeText(this, if (ok) "桌布群組已更新" else "桌布讀取失敗", Toast.LENGTH_SHORT).show()
                if (ok) applyWallpaper()
            }
        }
    }

    private val glbPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val dest = java.io.File(filesDir, "custom_car.glb")
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                updateCar3DView()
                Toast.makeText(this, "自訂 3D 車輛模型 (.glb) 已成功匯入", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "車輛模型讀取失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val decalPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val mime = contentResolver.getType(uri) ?: "image/png"
                    val dataUrl = "data:$mime;base64,$base64"
                    runOnUiThread {
                        val glbView = findViewById<ViewGroup>(R.id.carContainer)?.getChildAt(0) as? GlbCarView
                        glbView?.loadDecalDataUrl(dataUrl)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "讀取貼圖圖片失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchDecalImagePicker() {
        runOnUiThread {
            try { decalPicker.launch("image/*") } catch (_: Exception) {}
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            startGpsSpeedMonitor()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_home)

        try {
            locationPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } catch (_: Exception) {}

        box = findViewById(R.id.combos)
        btnMore = findViewById(R.id.btnMore)
        bgImage = findViewById(R.id.bgImage); bgVideo = findViewById(R.id.bgVideo)
        scrim = findViewById(R.id.scrim)
        scrollView = findViewById(R.id.scroll)
        video = VideoBg(bgVideo) {
            Toast.makeText(this, "影片無法播放,建議使用 H.264 編碼的 MP4", Toast.LENGTH_LONG).show()
        }

        val root = findViewById<ViewGroup>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            insTop = s.top; insBottom = s.bottom
            applyScrollPadding()
            insets
        }

        // 時鐘:獨立浮在桌面上,可長按拖曳位置、縮放大小
        clockPanel = ClockPanel(this)
        clock = clockPanel.clock; date = clockPanel.date
        root.addView(clockPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START
        ))

        // 右上角設定按鈕:依螢幕大小縮放的圓形玻璃按鈕
        val u = Ui.unit(this)
        btnMore.layoutParams.apply { width = (9f * u).toInt(); height = (9f * u).toInt() }
        (2.2f * u).toInt().let { btnMore.setPadding(it, it, it, it) }
        btnMore.background = GradientDrawable().apply { shape = GradientDrawable.OVAL }

        btnMore.setOnClickListener { showSettings() }
        scrollView.setOnLongClickListener { showSettings(); true }          // 長按空白處也能開設定
        findViewById<View>(R.id.btnApps).setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }

        updateCar3DView()
    }

    private fun updateCar3DView() {
        val carContainer = findViewById<ViewGroup>(R.id.carContainer) ?: return
        carContainer.removeAllViews()
        if (Store.car3DOn(this)) {
            carContainer.visibility = View.VISIBLE
            carContainer.addView(GlbCarView(this), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            carContainer.visibility = View.VISIBLE
            carContainer.addView(Turntable360View(this), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    override fun onResume() {
        super.onResume()
        applyScrollPadding()
        NavState.listener = { v -> animateReserve(v, true) }
        animateReserve(NavState.visible && Store.navBarOn(this), false)
        buildCombos()
        applyWallpaper()
        updateCar3DView()
        startGpsSpeedMonitor()
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        stopGpsSpeedMonitor()
        h.removeCallbacks(tick)
        NavState.listener = null
        clockPanel.setEditing(false)
        video.release()
    }

    private var locListener: android.location.LocationListener? = null

    private fun startGpsSpeedMonitor() {
        try {
            val lm = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager ?: return
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locListener = android.location.LocationListener { loc ->
                    val kmh = loc.speed * 3.6f
                    val bearing = if (loc.hasBearing()) loc.bearing else -1f
                    val glbView = findViewById<ViewGroup>(R.id.carContainer)?.getChildAt(0) as? GlbCarView
                    glbView?.setGpsData(kmh, bearing)
                }
                locListener?.let { lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000L, 0.5f, it) }
            }
        } catch (_: Exception) {}
    }

    private fun stopGpsSpeedMonitor() {
        try {
            val lm = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager ?: return
            locListener?.let { lm.removeUpdates(it) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        blurExec.shutdownNow()
    }

    private fun updateClock() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(now)
    }

    // ---------------- 設定選單 ----------------

    private fun showSettings() {
        SettingsMenu.show(this, btnMore, listOf(
            SettingsMenu.Item(R.drawable.ic_m_image, "桌布與背景美化設定") { showWallpaperSettingsDialog() },
            SettingsMenu.Item(R.drawable.ic_m_clock, "調整時鐘位置與大小", divider = true) { clockPanel.setEditing(true) },
            SettingsMenu.Item(R.drawable.ic_m_nav, "3D 車輛與系統導覽設定") { showSystemSettingsDialog() }
        ))
    }

    private fun showWallpaperSettingsDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_effects, null)
        val swBlur = v.findViewById<SwitchCompat>(R.id.swBlur)
        val sbBlur = v.findViewById<SeekBar>(R.id.sbBlur)
        val swDim = v.findViewById<SwitchCompat>(R.id.swDim)
        val sbDim = v.findViewById<SeekBar>(R.id.sbDim)
        val swRandom = v.findViewById<SwitchCompat>(R.id.swRandom)
        val btnPickWallpaper = v.findViewById<View>(R.id.btnPickWallpaper)
        val btnResetWallpaper = v.findViewById<View>(R.id.btnResetWallpaper)
        val note = v.findViewById<View>(R.id.note)

        swBlur.isChecked = Store.blurOn(this); sbBlur.progress = Store.blurStrength(this)
        sbBlur.isEnabled = swBlur.isChecked
        swDim.isChecked = Store.dimOn(this); sbDim.progress = Store.dimStrength(this)
        sbDim.isEnabled = swDim.isChecked
        swRandom.isChecked = Store.wallpaperRandomOn(this)
        note.visibility = if (Store.wallpaperIsVideo(this) && Build.VERSION.SDK_INT < 31) View.VISIBLE else View.GONE

        swBlur.setOnCheckedChangeListener { _, on ->
            Store.setBlurOn(this, on); sbBlur.isEnabled = on; applyWallpaper()
        }
        swDim.setOnCheckedChangeListener { _, on ->
            Store.setDimOn(this, on); sbDim.isEnabled = on; applyWallpaper()
        }
        swRandom.setOnCheckedChangeListener { _, on ->
            Store.setWallpaperRandomOn(this, on)
            Toast.makeText(this, "隨機輪播已${if (on) "開啟" else "關閉"}", Toast.LENGTH_SHORT).show()
            applyWallpaper()
        }
        sbBlur.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { Store.setBlurStrength(this@HomeActivity, p); applyWallpaper() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbDim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { Store.setDimStrength(this@HomeActivity, p); applyWallpaper() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnPickWallpaper.setOnClickListener {
            try {
                multiPicker.launch(arrayOf("image/*", "video/mp4"))
            } catch (e: Exception) {
                if (Wallpaper.importFolderFromAppFolder(this)) {
                    applyWallpaper()
                    Toast.makeText(this, "已從資料夾匯入全部桌布", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "無法開啟檔案選擇器，請將桌布放入 App 專屬資料夾", Toast.LENGTH_LONG).show()
                }
            }
        }
        btnResetWallpaper.setOnClickListener {
            Wallpaper.clear(this)
            applyWallpaper()
            Toast.makeText(this, "已還原預設桌布", Toast.LENGTH_SHORT).show()
        }

        val d = AlertDialog.Builder(this)
            .setTitle("桌布與背景美化設定")
            .setView(v)
            .setPositiveButton("完成", null)
            .create()
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    private fun showSystemSettingsDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_system, null)
        val swCar3D = v.findViewById<SwitchCompat>(R.id.swCar3D)
        val swRainbowRoad = v.findViewById<SwitchCompat>(R.id.swRainbowRoad)
        val swNavBar = v.findViewById<SwitchCompat>(R.id.swNavBar)
        val btnSplitManager = v.findViewById<View>(R.id.btnSplitManager)
        val btnImportGlb = v.findViewById<View>(R.id.btnImportGlb)
        val btnResetGlb = v.findViewById<View>(R.id.btnResetGlb)

        swCar3D.isChecked = Store.car3DOn(this)
        swCar3D.setOnCheckedChangeListener { _, on ->
            Store.setCar3DOn(this, on)
            updateCar3DView()
            Toast.makeText(this, if (on) "已開啟 3D 車輛桌面展廳" else "已切換至預設 360 轉盤", Toast.LENGTH_SHORT).show()
        }

        swRainbowRoad.isChecked = Store.rainbowRoadOn(this)
        swRainbowRoad.setOnCheckedChangeListener { _, on ->
            Store.setRainbowRoadOn(this, on)
            Toast.makeText(this, if (on) "已啟用彩虹大道模式" else "已關閉彩虹大道模式", Toast.LENGTH_SHORT).show()
        }

        btnImportGlb?.setOnClickListener {
            try {
                glbPicker.launch("*/*")
            } catch (_: Exception) {
                Toast.makeText(this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show()
            }
        }

        btnResetGlb?.setOnClickListener {
            val custom = java.io.File(filesDir, "custom_car.glb")
            if (custom.exists()) custom.delete()
            updateCar3DView()
            Toast.makeText(this, "已還原為內建預設 3D 車模", Toast.LENGTH_SHORT).show()
        }

        swNavBar.isChecked = Store.navBarOn(this)
        swNavBar.setOnCheckedChangeListener { _, on ->
            Store.setNavBarOn(this, on)
            val svc = NavService.inst
            svc?.nav?.refresh()
            applyScrollPadding()
            Toast.makeText(
                this,
                when {
                    svc == null -> "已${if (on) "開啟" else "關閉"},但需要先開啟「AIBOX 桌面」的無障礙服務才會顯示"
                    on -> "固定虛擬導覽列已開啟 (全寬自適應)"
                    else -> "虛擬導覽列已關閉"
                },
                Toast.LENGTH_LONG
            ).show()
        }

        btnSplitManager.setOnClickListener {
            startActivity(Intent(this, SplitManagerActivity::class.java))
        }

        val d = AlertDialog.Builder(this)
            .setTitle("3D 車輛與系統導覽設定")
            .setView(v)
            .setPositiveButton("完成", null)
            .create()
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    // ---------------- 導覽列出現時,下半部整個上移 ----------------

    private fun applyScrollPadding() {
        scrollView.setPadding(0, insTop, 0, insBottom + navExtra)
    }

    /**
     * 導覽列出現 → 桌面底部多預留一段高度,「組合方塊 + 全部 App」整個下半部一起平順上移;
     * 導覽列消失 → 一起移回最下方。(中間的彈性空白會自動被壓縮)
     */
    private fun animateReserve(navVisible: Boolean, animate: Boolean) {
        navAnim?.cancel()
        val contentPad = (scrollView as ViewGroup).getChildAt(0)?.paddingBottom ?: 0
        val target = if (navVisible) (NavOverlay.reservePx(this) - contentPad).coerceAtLeast(0) else 0
        if (!animate) { navExtra = target; applyScrollPadding(); return }
        navAnim = ValueAnimator.ofInt(navExtra, target).apply {
            duration = 180
            addUpdateListener { navExtra = it.animatedValue as Int; applyScrollPadding() }
            start()
        }
    }

    // ---------------- 桌布 ----------------

    private fun applyWallpaper() {
        val f = Wallpaper.file(this)
        val custom = f != null

        // 調暗(使用者可開關、可調強度,最高 85%)
        scrim.visibility = if (custom && Store.dimOn(this)) View.VISIBLE else View.GONE
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = Store.dimStrength(this) / 100f * 0.85f

        // 有自訂桌布時文字改白字加陰影,避免淺色桌布上看不清楚
        clock.setTextColor(if (custom) Color.WHITE else getColor(R.color.text))
        date.setTextColor(if (custom) 0xCCFFFFFF.toInt() else getColor(R.color.sub))
        val shadow = if (custom) 8f else 0f
        clock.setShadowLayer(shadow, 0f, 2f, 0x99000000.toInt())
        date.setShadowLayer(shadow, 0f, 2f, 0x99000000.toInt())
        btnMore.setColorFilter(if (custom) Color.WHITE else getColor(R.color.text))
        (btnMore.background as? GradientDrawable)?.setColor(if (custom) 0x40FFFFFF else getColor(R.color.chip))
        setLightBars(!custom && !isNight())

        if (f == null) {
            bgImage.setImageDrawable(null); bgVideo.visibility = View.GONE; loadedKey = null
            return
        }
        if (Store.wallpaperIsVideo(this)) {
            bgImage.setImageDrawable(null); loadedKey = null
            bgVideo.visibility = View.VISIBLE
            applyVideoBlur()
            video.play(f.path)
        } else {
            bgVideo.visibility = View.GONE
            showImage(f.path)
        }
    }

    private fun showImage(path: String) {
        val blur = Store.blurOn(this)
        val strength = Store.blurStrength(this)
        val key = if (blur) "$path|b$strength" else "$path|raw"
        if (key == loadedKey) return
        loadedKey = key
        val ver = ++blurVersion
        if (!blur) {
            val dm = resources.displayMetrics
            bgImage.setImageBitmap(Wallpaper.decode(path, dm.widthPixels, dm.heightPixels))
            return
        }
        blurExec.execute {
            if (ver != blurVersion) return@execute            // 已有更新的請求,放棄舊的
            val src = if (smallKey == path) small else Blur.small(path).also { small = it; smallKey = path }
            val out = src?.let { Blur.apply(it, strength) }
            runOnUiThread { if (ver == blurVersion && out != null) bgImage.setImageBitmap(out) }
        }
    }

    /** 影片虛化只有 Android 12(API 31)以上的系統支援 */
    private fun applyVideoBlur() {
        if (Build.VERSION.SDK_INT < 31) return
        if (Store.blurOn(this)) {
            val r = 1f + Store.blurStrength(this) / 100f * 39f
            bgVideo.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        } else {
            bgVideo.setRenderEffect(null)
        }
    }

    private fun buildCombos() {
        Folders.fill(this, box, Store.combos(this), false, Wallpaper.file(this) != null) {}
    }
}
