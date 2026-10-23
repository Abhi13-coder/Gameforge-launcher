package com.gameforge.booster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screenshot + screen recording via the public MediaProjection API — the
 * only non-root way for a 3rd-party app to capture the screen on modern
 * Android. Files are written to this app's own external-files directory
 * (scoped storage, no WRITE_EXTERNAL_STORAGE needed) and shared out via
 * FileProvider when the user taps the resulting notification.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    @Suppress("DEPRECATION")
    private fun legacyResultData(intent: Intent?): Intent? = intent?.getParcelableExtra(Constants.EXTRA_RESULT_DATA)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(Constants.EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(Constants.EXTRA_RESULT_DATA, Intent::class.java)
        else
            legacyResultData(intent)
        val mode = intent?.getStringExtra(Constants.EXTRA_MODE) ?: "screenshot"

        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(Constants.NOTIF_ID_CAPTURE, buildNotification("Starting capture…"))

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, resultData)
        mediaProjection = projection

        val metrics = DisplayMetrics().also { resources.displayMetrics }
        resources.displayMetrics.let {
            metrics.widthPixels = it.widthPixels
            metrics.heightPixels = it.heightPixels
            metrics.densityDpi = it.densityDpi
        }

        if (mode == "record") startRecording(projection, metrics) else takeScreenshot(projection, metrics)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Screenshot --------------------------------------------------

    private fun takeScreenshot(projection: MediaProjection, metrics: DisplayMetrics) {
        val reader = ImageReader.newInstance(
            metrics.widthPixels, metrics.heightPixels, android.graphics.PixelFormat.RGBA_8888, 2
        )
        imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * metrics.widthPixels
            val bitmap = Bitmap.createBitmap(
                metrics.widthPixels + rowPadding / plane.pixelStride,
                metrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(plane.buffer)
            image.close()

            val file = outputFile("Screenshot", "png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            bitmap.recycle()

            notifyCaptureReady(file, "Screenshot saved")
            handler.post { teardown() }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "gameforge-screenshot",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    // --- Recording ----------------------------------------------------

    private fun startRecording(projection: MediaProjection, metrics: DisplayMetrics) {
        val file = outputFile("Recording", "mp4")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()

        recorder.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(8_000_000)
            setOutputFile(file.absolutePath)
            prepare()
        }
        mediaRecorder = recorder

        virtualDisplay = projection.createVirtualDisplay(
            "gameforge-record",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface, null, handler
        )
        recorder.start()
        updateNotification("Recording… tap to stop")
    }

    fun stopRecording() {
        runCatching {
            mediaRecorder?.apply { stop(); reset(); release() }
        }
        mediaRecorder = null
        teardown()
        stopSelf()
    }

    // --- Shared teardown / notification --------------------------------

    private fun teardown() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun outputFile(prefix: String, ext: String): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GameForgeBooster").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "${prefix}_$stamp.$ext")
    }

    private fun notifyCaptureReady(file: File, title: String) {
        val uri = FileProvider.getUriForFile(this, "com.gameforge.booster.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (file.extension == "png") "image/png" else "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, Intent.createChooser(shareIntent, title),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            Constants.NOTIF_ID_CAPTURE,
            NotificationCompat.Builder(this, Constants.CHANNEL_CAPTURE)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(title)
                .setContentText(file.name)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_CAPTURE, "Screen capture", NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, Constants.CHANNEL_CAPTURE)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("GameForge Booster")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.NOTIF_ID_CAPTURE, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        teardown()
    }
}
