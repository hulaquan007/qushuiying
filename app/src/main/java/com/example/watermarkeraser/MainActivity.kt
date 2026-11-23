package com.example.watermarkeraser

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var maskView: MaskView
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var busy = false

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        maskView = findViewById(R.id.maskView)
        val seek = findViewById<SeekBar>(R.id.brushSeek)
        val density = resources.displayMetrics.density
        maskView.brushScreenPx = (seek.progress + 8) * density
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                maskView.brushScreenPx = (p + 8) * density
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnOpen).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Button>(R.id.btnClear).setOnClickListener { maskView.clearMask() }
        findViewById<Button>(R.id.btnErase).setOnClickListener { eraseWatermark() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveImage() }
    }

    private fun loadImage(uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (bmp == null) {
                toast(R.string.load_failed)
            } else {
                maskView.setImage(bmp)
            }
        } catch (e: Exception) {
            toast(R.string.load_failed)
        }
    }

    private fun eraseWatermark() {
        val bmp = maskView.currentBitmap()
        if (bmp == null) { toast(R.string.no_image); return }
        if (!maskView.hasMask()) { toast(R.string.no_mask); return }
        if (busy) return
        val mask = maskView.getMask() ?: return

        busy = true
        toast(R.string.processing)
        worker.execute {
            val result = try { Inpainter.inpaint(bmp, mask) } catch (e: Throwable) { null }
            ui.post {
                busy = false
                if (result != null) maskView.setImage(result)
            }
        }
    }

    private fun saveImage() {
        val bmp = maskView.currentBitmap()
        if (bmp == null) { toast(R.string.no_image); return }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "clean_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WatermarkEraser")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) { toast(R.string.save_failed); return }
        try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            toast(R.string.saved)
        } catch (e: Exception) {
            toast(R.string.save_failed)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    companion object {
        private const val MAX_SIDE = 3000
    }
}
