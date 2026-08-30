package com.stoumpos.wave;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

public class SleepTimerActivity extends Activity {

    private static final long TICK_INTERVAL_MILLIS = 1000L;

    private MusicService musicService;
    private boolean isBound = false;
    private TextView countdownText;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateCountdownText();
            tickHandler.postDelayed(this, TICK_INTERVAL_MILLIS);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            updateCountdownText();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_timer);

        countdownText = findViewById(R.id.sleepTimerCountdown);

        findViewById(R.id.sleepTimer15).setOnClickListener(v -> startTimer(15));
        findViewById(R.id.sleepTimer30).setOnClickListener(v -> startTimer(30));
        findViewById(R.id.sleepTimer45).setOnClickListener(v -> startTimer(45));
        findViewById(R.id.sleepTimer60).setOnClickListener(v -> startTimer(60));

        Button cancelButton = findViewById(R.id.sleepTimerCancel);
        cancelButton.setOnClickListener(v -> {
            if (isBound) {
                musicService.cancelSleepTimer();
                updateCountdownText();
            }
        });

        bindService(new Intent(this, MusicService.class), connection, Context.BIND_AUTO_CREATE);
    }

    private void startTimer(int minutes) {
        if (isBound) {
            musicService.startSleepTimer(minutes * 60_000L);
            updateCountdownText();
        }
    }

    private void updateCountdownText() {
        if (isBound && musicService.isSleepTimerActive()) {
            long remainingMillis = musicService.getSleepTimerRemainingMillis();
            long minutes = (remainingMillis / 60_000L);
            long seconds = (remainingMillis / 1000L) % 60L;
            countdownText.setText(getString(
                    R.string.sleep_timer_remaining,
                    String.format("%02d:%02d", minutes, seconds)));
        } else {
            countdownText.setText(R.string.sleep_timer_inactive);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tickHandler.post(tickRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        tickHandler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}
