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

                    SamplerScreen(
                        state = state,
                        micGranted = micGranted,
                        onRequestMic = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onToggleRecord = controller::toggleRecord,
                        onTogglePlay = controller::togglePlay,
                        onToggleLoop = controller::setLoop,
                        onReverse = controller::reverse,
                        onNormalize = controller::normalize,
                        onTrimChange = controller::setTrim,
                        onEffectEnabled = controller::setEffectEnabled,
                        onParamChange = controller::setEffectParam,
                        onExport = controller::exportWav,
                        onSavePreset = controller::savePreset,
                        onLoadPreset = controller::loadPreset,
                        onDeletePreset = controller::deletePreset,
                        onMessageShown = controller::consumeMessage
                    )
                }
            }
        }
    }
}
