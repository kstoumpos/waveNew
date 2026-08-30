package com.stoumpos.wave;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

/**
 * Fires when the radio alarm's scheduled time is reached. Starting a
 * foreground service from here is safe even if the app process is fully
 * killed — an AlarmManager firing is a documented exemption from the
 * Android 12+ background-service-start restriction.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent playIntent = new Intent(context, MusicService.class)
                .setAction(MusicService.ACTION_ALARM_PLAY);
        ContextCompat.startForegroundService(context, playIntent);

        AlarmScheduler.scheduleNextDailyOccurrence(context);
    }
}
