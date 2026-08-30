package com.stoumpos.wave;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores a previously-armed radio alarm after a device reboot. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlarmScheduler.rescheduleIfEnabled(context);
        }
    }
}
