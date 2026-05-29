package com.keyboardmasterpiece.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for LOCKED_BOOT_COMPLETED.
 * Ensures the keyboard service is available after device reboot
 * even before the user unlocks the device (directBootAware).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No action needed — the service is started by the system
        // when an input field is focused. This receiver exists to
        // ensure the app process is created in direct boot mode.
    }
}
