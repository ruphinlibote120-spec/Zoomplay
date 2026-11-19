package com.zoomplay.app

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand

/** Amplificateur (100 – 300 %) et égaliseur 5 bandes. */
object EqDialog {
    private val presets = linkedMapOf(
        "Normal" to Pair(100, intArrayOf(0, 0, 0, 0, 0)),
        "Basses+" to Pair(120, intArrayOf(7, 4, 0, -1, 0)),
        "Voix" to Pair(110, intArrayOf(-3, -1, 3, 4, 1)),
        "Aigus+" to Pair(100, intArrayOf(-2, 0, 1, 4, 7)),
        "Soirée" to Pair(150, intArrayOf(6, 3, 0, 3, 5))
    )
    private val names = arrayOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "14 kHz")

    fun show(ctx: Context, controller: MediaController?) {
        val store = Store(ctx)
        var boost = store.eqBoost
        val bands = store.eqBands
        val pad = ctx.dp(16f).toInt()

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, 0)

        fun send() {
            store.eqBoost = boost
            store.eqBands = bands
            controller?.sendCustomCommand(SessionCommand(PlaybackService.CMD_FX, Bundle.EMPTY), Bundle.EMPTY)
        }

        val boostLabel = TextView(ctx)
        boostLabel.setTextColor(ctx.getColor(R.color.text))
        boostLabel.textSize = 15f
        root.addView(boostLabel)
        val boostBar = SeekBar(ctx)
        boostBar.max = (300 - 100) / 5
        root.addView(boostBar)

        val bandBars = ArrayList<SeekBar>()
        val bandLabels = ArrayList<TextView>()

        fun refreshBoost() {
            boostLabel.text = "Amplificateur : $boost %"
        }

        fun refreshBands() {
            for (i in 0 until 5) {
                bandBars[i].progress = bands[i] + 12
                bandLabels[i].text = names[i] + "  " + (if (bands[i] > 0) "+" else "") + bands[i] + " dB"
            }
        }

        boostBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                boost = 100 + p * 5
                refreshBoost()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { send() }
        })

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        val scroll = android.widget.HorizontalScrollView(ctx)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(row)
        root.addView(scroll)

        for (i in 0 until 5) {
            val l = TextView(ctx)
            l.setTextColor(ctx.getColor(R.color.muted))
            l.textSize = 13f
            l.setPadding(0, pad / 2, 0, 0)
            root.addView(l)
            val b = SeekBar(ctx)
            b.max = 24
            b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    bands[i] = p - 12
                    l.text = names[i] + "  " + (if (bands[i] > 0) "+" else "") + bands[i] + " dB"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    store.eqPreset = "Perso"
                    send()
                }
            })
            root.addView(b)
            bandLabels.add(l)
            bandBars.add(b)
        }

        presets.forEach { (name, v) ->
            val btn = Button(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            btn.text = name
            btn.isAllCaps = false
            btn.setOnClickListener {
                boost = v.first
                for (i in 0 until 5) bands[i] = v.second[i]
                store.eqPreset = name
                boostBar.progress = (boost - 100) / 5
                refreshBoost()
                refreshBands()
                send()
            }
            row.addView(btn)
        }

        boostBar.progress = (boost - 100) / 5
        refreshBoost()
        refreshBands()

        val note = TextView(ctx)
        note.text = "Au-delà de 100 %, baisse un peu le volume si le son sature."
        note.setTextColor(ctx.getColor(R.color.muted))
        note.textSize = 12f
        note.setPadding(0, pad / 2, 0, 0)
        root.addView(note)

        val sv = android.widget.ScrollView(ctx)
        sv.addView(root)
        MaterialAlertDialogBuilder(ctx, R.style.ZoomDialog)
            .setTitle("Amplificateur et égaliseur")
            .setView(sv)
            .setPositiveButton("OK", null)
            .show()
    }
}
