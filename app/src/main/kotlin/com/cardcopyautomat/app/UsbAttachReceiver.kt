package com.cardcopyautomat.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Fires when any USB device is attached in host mode (e.g. the card reader
 * being plugged in, or a card being inserted into an already-connected
 * reader on readers that re-enumerate per card). Hands off to
 * CardCopyService to do the actual work.
 */
class UsbAttachReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val serviceIntent = Intent(context, CardCopyService::class.java).apply {
                    action = CardCopyService.ACTION_USB_ATTACHED
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                // Notify MainActivity to refresh if it's open (it will now see no card reader)
                val refreshIntent = Intent("com.cardcopyautomat.app.ACTION_REFRESH_UI")
                context.sendBroadcast(refreshIntent)
            }
        }
    }
}
