package com.voxora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voxora.app.dub.DubService
import com.voxora.app.dub.FloatingBubbleService
import com.voxora.app.ui.VoxoraNav
import com.voxora.app.ui.theme.VoxoraTheme
import com.voxora.core.prefs.UserPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val prefs by lazy { UserPrefs(applicationContext) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) {
            launchProjection()
        } else {
            DubService.postError(getString(R.string.error_permission_denied))
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            DubService.start(this, result.resultCode, result.data!!)
        } else {
            DubService.postError(getString(R.string.error_projection_denied))
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxoraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoxoraNav(
                        onStartDub = { requestStart() },
                        onStopDub = { DubService.stop(this) },
                        onDismissError = { DubService.clearError() },
                        onRequestOverlayPermission = { openOverlaySettings() },
                    )
                }
            }
        }
    }

    private fun requestStart() {
        lifecycleScope.launch {
            val key = prefs.apiKey.first()
            if (key.isBlank()) {
                DubService.postError(getString(R.string.error_no_api_key))
                return@launch
            }
            val need = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                need += Manifest.permission.RECORD_AUDIO
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                need += Manifest.permission.POST_NOTIFICATIONS
            }
            if (need.isNotEmpty()) {
                permissionLauncher.launch(need.toTypedArray())
            } else {
                launchProjection()
            }
        }
    }

    private fun launchProjection() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23 && !FloatingBubbleService.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
}
