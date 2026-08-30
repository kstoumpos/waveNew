package com.stoumpos.wave;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String STREAM_CONTENT_TYPE = "audio/mpeg";

    private MusicService musicService;
    private boolean isBound = false;
    private TextView loadingStatus;
    private ImageButton playButton;
    private ImageButton stopButton;
    private CastContext castContext;

    private final SessionManagerListener<CastSession> castSessionListener =
            new SessionManagerListener<CastSession>() {
        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            if (isBound) {
                musicService.stop();
            }
            loadStreamOntoCastSession(session);
            reconcileCastState();
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            reconcileCastState();
        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            // A session only ever ends while casting was active (local
            // playback is always stopped when a session starts), so this is
            // always a "go back to idle" transition, not a state to leave
            // alone the way reconcileCastState() does when nothing is
            // connected.
            loadingStatus.setVisibility(View.INVISIBLE);
            playButton.setVisibility(View.VISIBLE);
            stopButton.setVisibility(View.GONE);
        }

        @Override
        public void onSessionStarting(CastSession session) { }

        @Override
        public void onSessionStartFailed(CastSession session, int error) { }

        @Override
        public void onSessionEnding(CastSession session) { }

        @Override
        public void onSessionResuming(CastSession session, String sessionId) { }

        @Override
        public void onSessionResumeFailed(CastSession session, int error) { }

        @Override
        public void onSessionSuspended(CastSession session, int reason) { }
    };

    /**
     * Monitors the connection status with the MusicService.
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingStatus = findViewById(R.id.loadingStatus);
        playButton = findViewById(R.id.playButton);
        stopButton = findViewById(R.id.stopButton);

        ImageButton menuButton = findViewById(R.id.menuButton);
        menuButton.setOnClickListener(this::showMenu);

        castContext = CastContext.getSharedInstance(this);
        MediaRouteButton mediaRouteButton = findViewById(R.id.mediaRouteButton);
        CastButtonFactory.setUpMediaRouteButton(this, mediaRouteButton);

        // Bind to the MusicService. 
        // The service will be started as a foreground service when playback begins.
        Intent intent = new Intent(this, MusicService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // Refresh the cached stream URL from the remote config in the background.
        StreamConfig.refresh(this);

        // Force an immediate FCM token fetch (rather than waiting on the SDK's own
        // background init job, which some OEMs delay) and log the outcome for testing.
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("FCM", "Token: " + task.getResult());
            } else {
                Log.w("FCM", "Failed to get token", task.getException());
            }
        });

        // --- Playback Control Handlers ---
        // Single unified listener for Play button
        playButton.setOnClickListener(v -> {
            CastSession castSession = getCurrentCastSession();
            if (castSession != null && castSession.isConnected()) {
                loadStreamOntoCastSession(castSession);
                playButton.setVisibility(View.GONE);
                stopButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Casting stream", Toast.LENGTH_SHORT).show();
            } else if (isBound) {
                // Ensure the service is started in the foreground
                Intent serviceIntent = new Intent(this, MusicService.class);
                ContextCompat.startForegroundService(this, serviceIntent);

                loadingStatus.setVisibility(View.VISIBLE);
                musicService.play(StreamConfig.getCachedUrl(this), () -> runOnUiThread(() -> {
                    loadingStatus.setVisibility(View.INVISIBLE);
                    Toast.makeText(MainActivity.this,
                            "Now Playing", Toast.LENGTH_SHORT).show();
                    playButton.setVisibility(View.GONE);
                    stopButton.setVisibility(View.VISIBLE);
                }));
            } else {
                Toast.makeText(this,
                        "Connecting to music service...", Toast.LENGTH_SHORT).show();
            }
        });

        // Single unified listener for Stop button
        stopButton.setOnClickListener(v -> {
            CastSession castSession = getCurrentCastSession();
            if (castSession != null && castSession.isConnected()) {
                castContext.getSessionManager().endCurrentSession(true);
                stopButton.setVisibility(View.GONE);
                playButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Stopped casting", Toast.LENGTH_SHORT).show();
            } else if (isBound) {
                musicService.stop();
                loadingStatus.setVisibility(View.INVISIBLE);
                stopButton.setVisibility(View.GONE);
                playButton.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Stream Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        // --- Social Media Button Handlers ---
        findViewById(R.id.instagram).setOnClickListener(v -> openInstagram());
        findViewById(R.id.facebook).setOnClickListener(v -> openFacebook());
        findViewById(R.id.spotify).setOnClickListener(v -> openSpotify());
        findViewById(R.id.youtube).setOnClickListener(v -> openYouTube());
        findViewById(R.id.website).setOnClickListener(v -> openWebsite("https://www.wave974.gr/"));
    }

    private void showMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.main_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_sleep_timer) {
                startActivity(new Intent(this, SleepTimerActivity.class));
                return true;
            } else if (id == R.id.menu_radio_alarm) {
                startActivity(new Intent(this, RadioAlarmActivity.class));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private CastSession getCurrentCastSession() {
        SessionManager sessionManager = castContext.getSessionManager();
        return sessionManager.getCurrentCastSession();
    }

    private void loadStreamOntoCastSession(CastSession castSession) {
        RemoteMediaClient remoteMediaClient = castSession.getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }

        MediaInfo mediaInfo = new MediaInfo.Builder(StreamConfig.getCachedUrl(this))
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setContentType(STREAM_CONTENT_TYPE)
                .build();

        MediaLoadRequestData loadRequestData = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build();

        remoteMediaClient.load(loadRequestData);
    }

    /**
     * Keeps the Play/Stop buttons and local playback in sync with the actual
     * Cast session state. Needed because a Cast session can start/end from
     * outside this Activity (system Media Output Switcher, external
     * disconnect) while it isn't resumed, so the SessionManagerListener
     * callbacks alone can't be trusted as the sole source of truth.
     *
     * Only acts when a session is actually connected: it stops local
     * playback (if any) and shows the Stop button, since that state is known
     * for certain. When no session is connected it leaves the buttons alone
     * — local playback's own state isn't tracked here, so there's nothing
     * cast-specific to correct.
     */
    private void reconcileCastState() {
        CastSession castSession = getCurrentCastSession();
        boolean casting = castSession != null && castSession.isConnected();

        if (!casting) {
            return;
        }

        if (isBound) {
            musicService.stop();
        }
        loadingStatus.setVisibility(View.INVISIBLE);
        playButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.VISIBLE);
    }

    private void openFacebook() {
        Intent intent;
        try {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("fb://facewebmodal/f?href=https://www.facebook.com/Wave97.4"));
            startActivity(intent);
        } catch (Exception e) {
            intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.facebook.com/Wave97.4"));
            startActivity(intent);
        }
    }

    private void openSpotify() {
        String appUri = "spotify:user:5sle9af7m5jyf79nde75rvt5p";
        String webUrl = "https://open.spotify.com/user/5sle9af7m5jyf79nde75rvt5p";

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(appUri));
        intent.setPackage("com.spotify.music");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
        }
    }

    private void openInstagram() {
        Uri appUri = Uri.parse("https://instagram.com/_u/wave_97.4");
        Intent intent = new Intent(Intent.ACTION_VIEW, appUri);
        intent.setPackage("com.instagram.android");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Uri webUri = Uri.parse("https://instagram.com/wave_97.4");
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    private void openYouTube() {
        // vnd.youtube: is for video IDs, not channel IDs - using it with a channel
        // ID isn't recognized, so the app just opens to its own home screen instead
        // of the channel. The https channel URL with an explicit package works.
        Uri channelUri = Uri.parse("https://www.youtube.com/channel/UCEU3Mz0GbUo6r5Ly6NHg7kQ");
        Intent appIntent = new Intent(Intent.ACTION_VIEW, channelUri);
        appIntent.setPackage("com.google.android.youtube");

        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, channelUri));
        }
    }

    private void openWebsite(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        castContext.getSessionManager().addSessionManagerListener(castSessionListener, CastSession.class);
        reconcileCastState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        castContext.getSessionManager().removeSessionManagerListener(castSessionListener, CastSession.class);
    }

    /**
     * Proper lifecycle management: unbind from service to prevent memory leaks.
     * We do NOT stop the service here, allowing music to continue in the background.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}