package com.example.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * MusicService manages the MediaPlayer in a Foreground Service.
 * This ensures playback continues when the app is in the background
 * and provides methods for volume control and playback management.
 */
public class MusicService extends Service {

    private final IBinder binder = new MusicBinder();
    private MediaPlayer mediaPlayer;
    private static final String CHANNEL_ID = "MusicStreamChannel";
    private static final int NOTIFICATION_ID = 1;

    /**
     * Binder class for Activity-Service communication.
     */
    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initMediaPlayer();
        createNotificationChannel();
    }

    /**
     * Initializes the MediaPlayer with correct audio attributes for music.
     */
    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());

        // Release resources if the stream ends
        mediaPlayer.setOnCompletionListener(mp -> stopForeground(true));
    }

    /**
     * Starts streaming audio from a URL.
     * @param url The streaming endpoint.
     * @param preparedListener Callback for when buffering completes.
     */
    public void play(String url, MediaPlayer.OnPreparedListener preparedListener) {
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(url);

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                if (preparedListener != null) {
                    preparedListener.onPrepared(mp);
                }
            });

            mediaPlayer.prepareAsync();

            // Move service to foreground with a notification
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the stream and removes the foreground notification.
     */
    public void stop() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
        }
        stopForeground(true);
    }

    /**
     * Adjusts the volume of the MediaPlayer.
     * @param volume A float value between 0.0 and 1.0.
     */
    public void setVolume(float volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume, volume);
        }
    }

    /**
     * Creates the notification shown while music is playing.
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Stream Player Pro")
                .setContentText("Playing live stream...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // User cannot swipe away while playing
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /**
     * Required for Android O and above to show notifications.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }
}