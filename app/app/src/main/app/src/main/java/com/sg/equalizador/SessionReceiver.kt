package com.sg.equalizador

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!ctx.getSharedPreferences("eq", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        val sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val action = if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            EqService.ACTION_OPEN else EqService.ACTION_CLOSE
        try {
            ctx.startService(
                Intent(ctx, EqService::class.java).setAction(action).putExtra(EqService.EXTRA_SESSION, sid)
            )
        } catch (_: Exception) {}
    }
}
