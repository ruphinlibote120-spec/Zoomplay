package com.zoomplay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Écoute permanente du micro : « Zoom Play music », « Zoom Next », « Zoom Prev », « Zoom Pause ». */
class VoiceService : Service() {
    companion object {
        const val ACTION_STOP = "com.zoomplay.app.STOP_VOICE"
        private const val CHANNEL = "voice"
        private const val NOTIF_ID = 42
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var running = false
    private var handled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store(this).micOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForegroundCompat()
        } catch (e: Exception) {
            Store(this).micOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            connect()
            listen()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Écoute vocale", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Zoomplay t'écoute")
            .setContentText("Dis « Zoom Play music », « Zoom Next », « Zoom Prev » ou « Zoom Pause »")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Dis « Zoom Play music », « Zoom Next », « Zoom Prev » ou « Zoom Pause »")
            )
            .setContentIntent(open)
            .addAction(0, "Désactiver le micro", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun connect() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            try { controller = f.get() } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun listen() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail()
            return
        }
        handled = false
        rec?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) { inspect(partialResults) }
            override fun onResults(results: Bundle?) {
                inspect(results)
                again(300)
            }
            override fun onError(error: Int) {
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail()
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> again(1500)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> again(3000)
                    else -> again(400)
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { r.startListening(i) } catch (e: Exception) { again(1500) }
    }

    private fun again(delayMs: Long) {
        if (!running) return
        h.postDelayed({ listen() }, delayMs)
    }

    private fun inspect(b: Bundle?) {
        if (handled || b == null) return
        val list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (t in list) {
            val cmd = VoiceCommands.parse(t) ?: continue
            handled = true
            run(cmd)
            try { rec?.cancel() } catch (e: Exception) {}
            again(400)
            return
        }
    }

    private fun run(cmd: String) {
        val c = controller ?: return
        when (cmd) {
            "play" -> {
                if (c.mediaItemCount == 0) {
                    Thread {
                        val tracks = Library.scan(this)
                        h.post {
                            if (tracks.isNotEmpty()) {
                                c.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0L)
                                c.prepare()
                                c.play()
                            }
                        }
                    }.start()
                } else {
                    c.play()
                }
            }
            "next" -> c.seekToNext()
            "prev" -> c.seekToPrevious()
            "pause" -> c.pause()
        }
    }

    private fun fail() {
        Store(this).micOn = false
        running = false
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        future?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
