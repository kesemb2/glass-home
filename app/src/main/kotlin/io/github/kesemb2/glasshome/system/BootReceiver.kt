package io.github.kesemb2.glasshome.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.kesemb2.glasshome.MainActivity

/**
 * Opens Glass Home after the TV starts. Android only allows this when Glass Home is the default home app (the
 * system then opens it anyway) or has another exemption; otherwise the Home helper does it once it is connected.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !HomeSystem.replaceHomeEnabled(context)) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_HOME, true),
            )
        } catch (_: Exception) {
            // Background starts are blocked here; nothing else to do.
        }
    }
}
