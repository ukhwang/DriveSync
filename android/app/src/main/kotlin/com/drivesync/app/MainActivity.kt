package com.drivesync.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.drivesync.app.ui.DriveSyncRoot
import com.drivesync.app.ui.DriveSyncViewModel
import com.drivesync.app.ui.theme.DriveSyncTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DriveSyncViewModel by viewModels {
        DriveSyncViewModel.Factory((application as DriveSyncApplication).container)
    }

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Asked once while parked at launch; if denied, the memo button is simply hidden.
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            DriveSyncTheme {
                DriveSyncRoot(viewModel)
            }
        }
    }
}
