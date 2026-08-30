package com.stoumpos.wave;

import android.app.Activity;
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
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {

    private MusicService musicService;
    private boolean isBound = false;
    private TextView loadingStatus;
    private ImageButton playButton;
    private ImageButton stopButton;

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
            if (isBound) {
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
            if (isBound) {
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
        String appUri = "spotify:artist:5sle9af7m5jyf79nde75rvt5p";
        String webUrl = "https://open.spotify.com/artist/5sle9af7m5jyf79nde75rvt5p";

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
        Intent appIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("vnd.youtube:UCEU3Mz0GbUo6r5Ly6NHg7kQ"));
        Intent webIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/channel/UCEU3Mz0GbUo6r5Ly6NHg7kQ"));

        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException e) {
            startActivity(webIntent);
        }
    }

    private void openWebsite(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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