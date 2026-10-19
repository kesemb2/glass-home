package io.github.kesemb2.glasshome

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.github.kesemb2.glasshome.diagnostics.DeviceDiagnostics
import io.github.kesemb2.glasshome.ui.DiagnosticsScreen

class MainActivity : ComponentActivity() {

    private val diagnostics = mutableStateOf<DeviceDiagnostics?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiagnosticsScreen(
                diagnostics = diagnostics.value,
                onRefresh = ::refreshDiagnostics,
                onAppInfo = ::openAppInfo,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
    }

    private fun refreshDiagnostics() {
        diagnostics.value = DeviceDiagnostics.read(this)
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some TV builds strip the app-details screen; nothing else to offer here.
        }
    }
}
