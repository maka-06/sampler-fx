package com.example.sampleapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sampleapp.audio.AudioController
import com.example.sampleapp.ui.SamplerScreen
import com.example.sampleapp.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {

    private val controller: AudioController by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by controller.state.collectAsStateWithLifecycle()
                    val context = LocalContext.current

                    var micGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        micGranted = granted
                        if (granted) controller.toggleRecord()
                    }

                    val createDocLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("audio/x-wav")
                    ) { uri ->
                        if (uri != null) {
                            context.contentResolver.openFileDescriptor(uri, "w")?.let { pfd ->
                                controller.exportToFd(pfd.detachFd())
                            }
                        }
                    }

                    SamplerScreen(
                        controller = controller,
                        state = state,
                        micGranted = micGranted,
                        onRequestMic = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onExport = {
                            if (controller.hasSample()) {
                                createDocLauncher.launch(controller.suggestedFileName())
                            }
                        },
                        onMessageShown = controller::consumeMessage
                    )
                }
            }
        }
    }
}
