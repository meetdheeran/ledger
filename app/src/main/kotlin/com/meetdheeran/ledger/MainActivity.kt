package com.meetdheeran.ledger

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetdheeran.ledger.ui.CreatePasswordScreen
import com.meetdheeran.ledger.ui.HomeScreen
import com.meetdheeran.ledger.ui.ImportingScreen
import com.meetdheeran.ledger.ui.Ink
import com.meetdheeran.ledger.ui.LedgerTheme
import com.meetdheeran.ledger.ui.PermissionScreen
import com.meetdheeran.ledger.ui.UnlockScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /**
     * The permission dialog puts this activity through onStop on some OEM
     * builds. Without this flag the app would re-lock behind the dialog and the
     * user would come back to a password prompt instead of their data.
     */
    private var awaitingPermissionDialog = false

    private lateinit var permissions: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        permissions = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { granted ->
            awaitingPermissionDialog = false
            vm.onPermissionResult(granted[Manifest.permission.READ_SMS] == true)
        }

        setContent {
            LedgerTheme {
                Surface(color = Ink.bg) {
                    val gate by vm.gate.collectAsStateWithLifecycle()
                    val unlockError by vm.unlockError.collectAsStateWithLifecycle()

                    when (val g = gate) {
                        Gate.Loading -> ImportingScreen("")
                        Gate.CreatePassword -> CreatePasswordScreen { vm.createPassword(it) }
                        Gate.Unlock -> UnlockScreen(unlockError) { vm.unlock(it) }
                        Gate.NeedPermission -> PermissionScreen { requestSmsPermissions() }
                        is Gate.Importing -> ImportingScreen(g.note)
                        Gate.Ready -> HomeScreen(vm)
                    }
                }
            }
        }
    }

    private fun requestSmsPermissions() {
        awaitingPermissionDialog = true
        permissions.launch(
            arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        )
    }

    /**
     * Leaving the app locks it again. A password that is only asked once after a
     * reboot is decoration; this is the behaviour that makes it mean something.
     */
    override fun onStop() {
        super.onStop()
        if (!awaitingPermissionDialog) vm.relock()
    }
}
