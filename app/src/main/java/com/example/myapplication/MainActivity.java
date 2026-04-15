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
    private final String currentUrl = "https://sp1.32bit.gr/8018/;";

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

        // Start and Bind to the background MusicService.
        // Calling startService ensures the service lives independently of the activity.
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // --- Playback Control Handlers ---
        ImageButton playButton = findViewById(R.id.playButton);
        ImageButton stopButton = findViewById(R.id.stopButton);

        findViewById(R.id.playButton).setOnClickListener(v -> {
            if (isBound) {
                loadingStatus.setVisibility(View.VISIBLE);
                musicService.play(currentUrl, () -> {
                    // This callback is triggered by the Service when buffering completes and playback starts
                    runOnUiThread(() -> {
                        loadingStatus.setVisibility(View.INVISIBLE);
                        Toast.makeText(MainActivity.this, "Now Playing", Toast.LENGTH_SHORT).show();
                    });
                });
            } else {
                Toast.makeText(this, "Connecting to music service...", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.stopButton).setOnClickListener(v -> {
            if (isBound) {
                musicService.stop();
                loadingStatus.setVisibility(View.INVISIBLE);
                Toast.makeText(this, "Stream Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        playButton.setOnClickListener(v -> musicService.play(currentUrl, () -> runOnUiThread(() -> {
            playButton.setVisibility(View.GONE);
            stopButton.setVisibility(View.VISIBLE);
        })));

        stopButton.setOnClickListener(v -> {
            musicService.stop();
            stopButton.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
        });

        // --- Social Media Button Placeholders ---
        findViewById(R.id.instagram).setOnClickListener(v -> openInstagram());

        // Facebook
        findViewById(R.id.facebook).setOnClickListener(v -> openFacebook());

        // Spotify
        findViewById(R.id.spotify).setOnClickListener(v -> openSpotify());

        // YouTube Button
        findViewById(R.id.youtube).setOnClickListener(v -> openYouTube());

        // Website Button
        findViewById(R.id.website).setOnClickListener(v -> openWebsite("https://www.wave974.gr/"));
    }

    private void openFacebook() {
        Intent intent;
        try {
            // This URI scheme works best for opening specific pages in the FB app
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("fb://facewebmodal/f?href=" + "https://www.facebook.com/Wave97.4"));
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to browser
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/Wave97.4"));
            startActivity(intent);
        }
    }

    private void openSpotify() {
        String appUri = "spotify:artist:" + "5sle9af7m5jyf79nde75rvt5p";
        String webUrl = "https://open.spotify.com/artist/" + "5sle9af7m5jyf79nde75rvt5p";

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(appUri));
        // Explicitly target the Spotify package
        intent.setPackage("com.spotify.music");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Fallback to browser
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)));
        }
    }

    private void openInstagram() {
        // The "_u/" tells Instagram to open the user profile specifically
        Uri appUri = Uri.parse("https://instagram.com/_u/" + "wave_97.4");
        Intent intent = new Intent(Intent.ACTION_VIEW, appUri);

        // Explicitly target the Instagram app package
        intent.setPackage("com.instagram.android");

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // If the app is not installed, open the standard web URL in a browser
            Uri webUri = Uri.parse("https://instagram.com/" + "wave_97.4");
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    private void openYouTube() {
        // Note: Use the Channel ID (e.g., UCxxxxxxxxxxxx)
        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + "UCEU3Mz0GbUo6r5Ly6NHg7kQ"));
        Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/channel/" + "UCEU3Mz0GbUo6r5Ly6NHg7kQ"));

        try {
            // Try to open the YouTube app
            startActivity(appIntent);
        } catch (ActivityNotFoundException e) {
            // Fallback to the browser
            startActivity(webIntent);
        }
    }

    private void openWebsite(String url) {
        // Ensure the URL starts with http:// or https://
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
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