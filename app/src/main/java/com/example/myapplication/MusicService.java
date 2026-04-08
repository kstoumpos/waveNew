package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

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

    private ExoPlayer player;

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

        // Start foreground service immediately (Android 14 requirement)
        Notification notification = new NotificationCompat.Builder(this, "radio_channel")
                .setContentTitle("Radio")
                .setContentText("Preparing stream…")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(1, notification);
        }

        // Create ExoPlayer
        player = new ExoPlayer.Builder(this).build();
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
                    onStarted.run();
                }
            }
        });
    }

    public void stop() {
        if (player != null) {
            player.stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new MusicBinder();
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }
}