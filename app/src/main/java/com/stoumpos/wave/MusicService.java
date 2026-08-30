package com.stoumpos.wave;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * A media3 MediaLibraryService so the radio stream is controllable from
 * Android Auto, Bluetooth/headset buttons, and the system media notification
 * — not just this app's own UI. Wave has a single "station", so the browse
 * tree exposed to those surfaces is just one playable root item.
 */
@OptIn(markerClass = UnstableApi.class)
public class MusicService extends MediaLibraryService {

    public static final String ACTION_ALARM_PLAY = "com.stoumpos.wave.ACTION_ALARM_PLAY";

    private static final String ROOT_ID = "root";
    private static final String LIVE_ITEM_ID = "wave_live";

    private Player player;
    private MediaLibrarySession mediaLibrarySession;
    private byte[] launcherArtworkData;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        // Any loss (including a transient one, e.g. an incoming/active phone call)
        // is treated as a full stop, same as an externally-issued Pause - a live
        // stream shouldn't silently resume into stale buffered audio once focus is
        // returned.
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            stop();
        }
    };

    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable sleepTimerRunnable;
    private long sleepTimerEndAtMillis = 0L;

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = getSystemService(AudioManager.class);
        launcherArtworkData = renderLauncherIconToPng();

        // Same channel media3's own DefaultMediaNotificationProvider uses, created
        // eagerly so our own synchronous startForeground() call in onStartCommand()
        // (below) always has a valid channel to post to.
        NotificationChannel channel = new NotificationChannel(
                DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID,
                getString(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_NAME_RESOURCE_ID),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        // The framework's own notification defaults to a generic media3 icon.
        DefaultMediaNotificationProvider notificationProvider =
                new DefaultMediaNotificationProvider.Builder(this).build();
        notificationProvider.setSmallIcon(R.mipmap.ic_launcher);
        setMediaNotificationProvider(notificationProvider);

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true);

        ExoPlayer exoPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new ProgressiveMediaSource.Factory(dataSourceFactory))
                .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ false)
                .build();
        player = new RadioPlayer(exoPlayer);

        mediaLibrarySession = new MediaLibrarySession.Builder(this, player, new LibraryCallback())
                .build();
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaLibrarySession;
    }

    /**
     * R.mipmap.ic_launcher is an adaptive icon (an XML drawable made of layers) on
     * API 26+, not a flat image file - handing its URI to MediaMetadata.setArtworkUri()
     * silently fails because media3's loader decodes it as raw image bytes. Rendering
     * it to a real bitmap ourselves via the Drawable API handles adaptive icons
     * correctly, then it's supplied as raw data instead of a URI.
     */
    private byte[] renderLauncherIconToPng() {
        Drawable drawable = ContextCompat.getDrawable(this, R.mipmap.ic_launcher);
        int size = 256;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        return outputStream.toByteArray();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // MediaSessionService.onBind() serves MediaController connections for its own
        // intent actions and returns null otherwise - fall back to our own binder for
        // MainActivity/SleepTimerActivity's plain same-process binds.
        IBinder binder = super.onBind(intent);
        return binder != null ? binder : new MusicBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Required: MediaSessionService.onStartCommand() is @CallSuper and handles
        // real media-button events (e.g. Bluetooth play/pause).
        super.onStartCommand(intent, flags, startId);

        Notification placeholder = new NotificationCompat.Builder(
                this, DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
                .setContentTitle("Wave 97.4")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);

        if (intent != null && ACTION_ALARM_PLAY.equals(intent.getAction())) {
            play(StreamConfig.getCachedUrl(this), () -> { /* no UI to callback into */ });
        }

        // Not START_STICKY: a restart with no Intent has no session/URL to resume, and
        // START_STICKY is flaky on API 27-33 under memory pressure (androidx/media#459).
        return START_NOT_STICKY;
    }

    public void play(String url, Runnable onStarted) {
        requestAudioFocus();
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();

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
        cancelSleepTimer();
        abandonAudioFocus();
        if (player != null) {
            player.stop();
            // clearMediaItems() (not just stop()) is what tears down the foreground
            // notification immediately instead of leaving it up to 10 minutes.
            player.clearMediaItems();
        }
        stopSelf();
    }

    private void requestAudioFocus() {
        android.media.AudioAttributes platformAudioAttributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(platformAudioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build();

        audioManager.requestAudioFocus(audioFocusRequest);
    }

    private void abandonAudioFocus() {
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
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
    public void onDestroy() {
        cancelSleepTimer();
        if (mediaLibrarySession != null) {
            mediaLibrarySession.release();
            mediaLibrarySession = null;
        }
        if (player != null) {
            player.release();
        }
        super.onDestroy();
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    /**
     * Treats an externally-issued Pause (Android Auto, the system notification,
     * Bluetooth headset buttons) as a full Stop instead of a real pause/resume.
     * A live stream shouldn't "resume" into stale buffered audio, and a real
     * pause would also leave the sleep timer running silently in the background.
     */
    private static class RadioPlayer extends ForwardingPlayer {
        RadioPlayer(Player player) {
            super(player);
        }

        @Override
        public void pause() {
            stop();
        }
    }

    private class LibraryCallback implements MediaLibrarySession.Callback {

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params) {
            MediaItem root = new MediaItem.Builder()
                    .setMediaId(ROOT_ID)
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setTitle("Wave 97.4")
                            .setArtworkData(launcherArtworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build())
                    .build();
            return Futures.immediateFuture(LibraryResult.ofItem(root, params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                @Nullable LibraryParams params) {
            if (!ROOT_ID.equals(parentId)) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params));
            }
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.of(buildLiveMediaItem()), params));
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession session,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems) {
            // Re-stamp the stream URL at play time rather than trusting whatever was
            // cached when the browse tree was built - StreamConfig can refresh in the
            // background between browsing and actually pressing play.
            return Futures.immediateFuture(ImmutableList.of(buildLiveMediaItem()));
        }

        private MediaItem buildLiveMediaItem() {
            return new MediaItem.Builder()
                    .setMediaId(LIVE_ITEM_ID)
                    .setUri(StreamConfig.getCachedUrl(MusicService.this))
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setTitle("Wave 97.4 – Live")
                            .setArtworkData(launcherArtworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                            .build())
                    .build();
        }
    }
}
