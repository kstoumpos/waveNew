package com.stoumpos.wave;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Schedules the daily-repeating radio alarm via AlarmManager.setAlarmClock(),
 * which is exempt from the SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM permission
 * checks since it's designed for exactly this use case (a user-visible,
 * user-scheduled wake alarm). Alarms are cleared on reboot regardless of
 * scheduling method, so BootReceiver calls rescheduleIfEnabled() to restore
 * them.
 */
public class AlarmScheduler {

    private static final String PREFS_NAME = "radio_alarm";
    private static final String KEY_ENABLED = "alarm_enabled";
    private static final String KEY_HOUR = "alarm_hour";
    private static final String KEY_MINUTE = "alarm_minute";

    private static final int REQUEST_CODE_OPERATION = 100;
    private static final int REQUEST_CODE_SHOW = 101;

    private AlarmScheduler() {
    }

    public static void enable(Context context, int hour, int minute) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
        scheduleAlarmClock(context, nextTriggerMillis(hour, minute));
    }

    public static void disable(Context context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply();
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(operationPendingIntent(context));
    }

    /** Called by BootReceiver after a reboot to restore a previously-armed alarm. */
    public static void rescheduleIfEnabled(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        scheduleAlarmClock(context, nextTriggerMillis(getHour(context), getMinute(context)));
    }

    /** Called by AlarmReceiver right after it fires, to re-arm tomorrow's occurrence. */
    public static void scheduleNextDailyOccurrence(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, getHour(context));
        next.set(Calendar.MINUTE, getMinute(context));
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.DAY_OF_YEAR, 1);
        scheduleAlarmClock(context, next.getTimeInMillis());
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static int getHour(Context context) {
        return prefs(context).getInt(KEY_HOUR, 7);
    }

    public static int getMinute(Context context) {
        return prefs(context).getInt(KEY_MINUTE, 0);
    }

    private static long nextTriggerMillis(int hour, int minute) {
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) {
            trigger.add(Calendar.DAY_OF_YEAR, 1);
        }
        return trigger.getTimeInMillis();
    }

    private static void scheduleAlarmClock(Context context, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(
                triggerAtMillis, showPendingIntent(context));
        alarmManager.setAlarmClock(info, operationPendingIntent(context));
    }

    private static PendingIntent operationPendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE_OPERATION, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent showPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(
                context, REQUEST_CODE_SHOW, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
