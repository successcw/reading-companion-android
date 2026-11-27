package com.cg.readingcompanion.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val recordPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.toggleRecording() }

    private lateinit var viewModel: AppViewModel

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            WakeBroadcaster.send(viewModel.state.value.address)
        }
    }

    override fun onResume() {
        super.onResume()
        WakeBroadcaster.send(viewModel.state.value.address)
        runCatching {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onPause() {
        runCatching {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(networkCallback)
        }
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = androidx.lifecycle.ViewModelProvider(this)[AppViewModel::class.java]
        LocalServerService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent {
            ReadingCompanionTheme {
                AppScreen(
                    vm = viewModel,
                    onMicTap = {
                        if (ContextCompat.checkSelfPermission(
                                this, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.toggleRecording()
                        } else {
                            recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        }
    }
}
