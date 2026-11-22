package com.bassim.stickmanstudio

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

class MainActivity : Activity() {
    private lateinit var stage: StickmanStage
    private lateinit var frameLabel: TextView
    private var playing = false
    private var playThread: Thread? = null
    private var frame = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 27, 42)
        window.navigationBarColor = Color.rgb(16, 27, 42)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(16, 27, 42))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val header = TextView(this).apply {
            text = "استوديو رجل العصا"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(12))
        }
        page.addView(header)

        val sub = TextView(this).apply {
            text = "محرر رسوم متحركة ثنائي الأبعاد"
            textSize = 12f
            setTextColor(Color.rgb(170, 190, 205))
            gravity = Gravity.CENTER
        }
        page.addView(sub)

        stage = StickmanStage(this)
        page.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })

        val controls1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        controls1.addView(actionButton("＋ شخصية") { stage.addCharacter() })
        controls1.addView(actionButton("حذف") { stage.removeCharacter() })
        controls1.addView(actionButton("حفظ") { saveProject() })
        controls1.addView(actionButton("استعادة") { loadProject() })
        page.addView(controls1)

        val controls2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        controls2.addView(actionButton("▶ تشغيل") { togglePlayback() })
        controls2.addView(actionButton("إطار سابق") { frame = (frame - 1).coerceAtLeast(0); stage.setFrame(frame); updateFrame() })
        controls2.addView(actionButton("إطار تالٍ") { frame = (frame + 1).coerceAtMost(120); stage.setFrame(frame); updateFrame() })
        page.addView(controls2)

        frameLabel = TextView(this).apply {
            text = "الإطار: 0 / 120"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        page.addView(frameLabel)

        val timeline = SeekBar(this).apply {
            max = 120
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { frame = progress; stage.setFrame(frame); updateFrame() }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        page.addView(timeline)

        val note = TextView(this).apply {
            text = "نسخة تأسيسية: الرسم والتحريك الأساسي والحفظ المحلي قيد التجهيز للتوسعة."
            textSize = 10f
            setTextColor(Color.rgb(145, 165, 180))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        page.addView(note)

        setContentView(page)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 135, 145))
            setPadding(dp(3), 0, dp(3), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            }
            setOnClickListener { action() }
        }

    private fun updateFrame() { frameLabel.text = "الإطار: $frame / 120" }

    private fun togglePlayback() {
        playing = !playing
        if (!playing) return
        Thread {
            while (playing) {
                Thread.sleep(80)
                frame = if (frame >= 120) 0 else frame + 1
                runOnUiThread { stage.setFrame(frame); updateFrame() }
            }
        }.also { playThread = it; it.start() }
    }

    private fun saveProject() {
        getSharedPreferences("stickman_project", Context.MODE_PRIVATE).edit()
            .putString("scene", stage.exportState()).apply()
        Toast.makeText(this, "تم حفظ المشروع على الهاتف", Toast.LENGTH_SHORT).show()
    }

    private fun loadProject() {
        val saved = getSharedPreferences("stickman_project", Context.MODE_PRIVATE).getString("scene", null)
        if (saved == null) {
            Toast.makeText(this, "لا يوجد مشروع محفوظ بعد", Toast.LENGTH_SHORT).show()
        } else {
            stage.importState(saved)
            Toast.makeText(this, "تمت استعادة المشروع", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        playing = false
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private data class StickFigure(var x: Float, var y: Float, var scale: Float = 1f, var phase: Float = 0f)

class StickmanStage(context: Context) : View(context) {
    private val figures = mutableListOf(StickFigure(0.5f, 0.58f))
    private var currentFrame = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 35, 48)
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(225, 235, 238); strokeWidth = 1f }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(239, 246, 247) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, bg)
        for (i in 1..5) canvas.drawLine(0f, height * i / 6f, width.toFloat(), height * i / 6f, grid)
        for (figure in figures) drawFigure(canvas, figure)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(95, 115, 125); textSize = 12f; style = Paint.Style.FILL
        }
        canvas.drawText("مساحة المشهد", 18f, 28f, label)
    }

    private fun drawFigure(canvas: Canvas, f: StickFigure) {
        val cx = width * f.x
        val cy = height * f.y
        val s = min(width, height) * 0.22f * f.scale
        val swing = sin((currentFrame / 12f) + f.phase) * s * 0.22f
        val p = Paint(paint).apply { strokeWidth = max(3f, s * 0.055f) }
        canvas.drawCircle(cx, cy - s * 1.05f, s * 0.25f, fill)
        canvas.drawCircle(cx, cy - s * 1.05f, s * 0.25f, p)
        canvas.drawLine(cx, cy - s * 0.78f, cx, cy + s * 0.05f, p)
        canvas.drawLine(cx, cy - s * 0.62f, cx - s * 0.48f + swing, cy - s * 0.18f, p)
        canvas.drawLine(cx, cy - s * 0.62f, cx + s * 0.48f - swing, cy - s * 0.18f, p)
        canvas.drawLine(cx, cy + s * 0.05f, cx - s * 0.38f + swing, cy + s * 0.72f, p)
        canvas.drawLine(cx, cy + s * 0.05f, cx + s * 0.38f - swing, cy + s * 0.72f, p)
    }

    fun setFrame(frame: Int) { currentFrame = frame; invalidate() }
    fun addCharacter() {
        figures.add(StickFigure((0.25f + (figures.size % 3) * 0.24f).coerceAtMost(0.85f), 0.58f, 0.85f, figures.size.toFloat()))
        invalidate()
    }
    fun removeCharacter() { if (figures.size > 1) figures.removeAt(figures.lastIndex); invalidate() }

    fun exportState(): String {
        val array = JSONArray()
        figures.forEach {
            array.put(JSONObject().put("x", it.x).put("y", it.y).put("scale", it.scale).put("phase", it.phase))
        }
        return JSONObject().put("frame", currentFrame).put("figures", array).toString()
    }

    fun importState(raw: String) {
        try {
            val obj = JSONObject(raw)
            currentFrame = obj.optInt("frame", 0)
            val arr = obj.getJSONArray("figures")
            figures.clear()
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                figures.add(StickFigure(f.optDouble("x", .5).toFloat(), f.optDouble("y", .58).toFloat(),
                    f.optDouble("scale", 1.0).toFloat(), f.optDouble("phase", 0.0).toFloat()))
            }
            if (figures.isEmpty()) figures.add(StickFigure(.5f, .58f))
            invalidate()
        } catch (_: Exception) { }
    }
}
