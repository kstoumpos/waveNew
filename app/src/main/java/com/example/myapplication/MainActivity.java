package com.example.myapplication;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

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

        // Start and Bind to the background MusicService.
        // Calling startService ensures the service lives independently of the activity.
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // Refresh the cached stream URL from the remote config in the background.
        StreamConfig.refresh(this);

        // --- Playback Control Handlers ---
        // Single unified listener for Play button
        playButton.setOnClickListener(v -> {
            if (isBound) {
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