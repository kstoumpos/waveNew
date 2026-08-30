package com.stoumpos.wave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

public class MusicService extends Service {

    public static final String ACTION_ALARM_PLAY = "com.stoumpos.wave.ACTION_ALARM_PLAY";

    private ExoPlayer player;

    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;
    private long sleepTimerEndAtMillis = 0L;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create notification channel for foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "radio_channel",
                    "Radio Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // Create ExoPlayer
        player = new ExoPlayer.Builder(this).build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground service (Android 14 requirement)
        Notification notification = buildNotification("Preparing stream…");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, notification);
        }

        if (intent != null && ACTION_ALARM_PLAY.equals(intent.getAction())) {
            play(StreamConfig.getCachedUrl(this), () -> { /* no UI to callback into */ });
        }

        return START_STICKY;
    }

    private Notification buildNotification(String contentText) {
        return new NotificationCompat.Builder(this, "radio_channel")
                .setContentTitle("Wave 97.4")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(1, buildNotification(contentText));
    }

    @OptIn(markerClass = UnstableApi.class)
    public void play(String url, Runnable onStarted) {

        DefaultHttpDataSource.Factory dataSourceFactory =
                new DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0")
                        .setAllowCrossProtocolRedirects(true);

        Uri uri = Uri.parse(url);

        MediaSource mediaSource =
                new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(uri));

        player.setMediaSource(mediaSource);
        player.prepare();
        player.play();

        // Callback when playback actually starts
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateNotification("Playing stream");
                    onStarted.run();
                }
            }
        });
    }

    public void stop() {
        cancelSleepTimer();
        if (player != null) {
            player.stop();
        }
        updateNotification("Stream stopped");
        stopForeground(true);
        stopSelf();
    }

    /** Stops playback automatically after delayMillis, cancelling any existing timer. */
    public void startSleepTimer(long delayMillis) {
        cancelSleepTimer();
        sleepTimerEndAtMillis = System.currentTimeMillis() + delayMillis;
        sleepTimerRunnable = this::stop;
        sleepTimerHandler.postDelayed(sleepTimerRunnable, delayMillis);
    }

    public void cancelSleepTimer() {
        if (sleepTimerRunnable != null) {
            sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
            sleepTimerRunnable = null;
        }
        sleepTimerEndAtMillis = 0L;
    }

    public boolean isSleepTimerActive() {
        return sleepTimerRunnable != null;
    }

    public long getSleepTimerRemainingMillis() {
        return sleepTimerEndAtMillis == 0L
                ? 0L
                : Math.max(0L, sleepTimerEndAtMillis - System.currentTimeMillis());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MusicBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelSleepTimer();
        if (player != null) {
            player.release();
        }
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }
}