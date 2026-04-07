package com.example.myapplication;

import android.app.Activity;
import android.os.Bundle;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private MusicService musicService;
    private boolean isBound = false;
    private TextView loadingStatus;

    // Default streaming URL - can be updated via the "Favorite" feature
    private String currentUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
    private SharedPreferences prefs;

    /**
     * Monitors the connection status with the MusicService.
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;

            // Sync initial volume from the SeekBar to the Service once connected
            SeekBar sb = findViewById(R.id.volumeSeekBar);
            if (sb != null && musicService != null) {
                // Map 0-100 progress to 0.0f-1.0f float for MediaPlayer
                musicService.setVolume(sb.getProgress() / 100f);
            }
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

        // Initialize SharedPreferences for saving the favorite stream
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        loadingStatus = findViewById(R.id.loadingStatus);

        // Load favorite URL if previously saved by the user
        String savedFav = prefs.getString("fav_url", "");
        if (!savedFav.isEmpty()) {
            currentUrl = savedFav;
            Toast.makeText(this, "Favorite stream loaded", Toast.LENGTH_SHORT).show();
        }

        // Start and Bind to the background MusicService.
        // Calling startService ensures the service lives independently of the activity.
        Intent intent = new Intent(this, MusicService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // --- Playback Control Handlers ---

        findViewById(R.id.playButton).setOnClickListener(v -> {
            if (isBound) {
                loadingStatus.setVisibility(View.VISIBLE);
                musicService.play(currentUrl, mp -> {
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

        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            if (isBound) {
                musicService.stop();
                loadingStatus.setVisibility(View.INVISIBLE);
                Toast.makeText(this, "Stream Stopped", Toast.LENGTH_SHORT).show();
            }
        });

        // --- Favorite Stream Logic ---

        findViewById(R.id.favButton).setOnClickListener(v -> {
            // Persist the current URL to local storage
            prefs.edit().putString("fav_url", currentUrl).apply();
            Toast.makeText(this, "Stream saved as favorite!", Toast.LENGTH_SHORT).show();
        });

        // --- Volume Slider Logic ---

        SeekBar volumeBar = findViewById(R.id.volumeSeekBar);
        if (volumeBar != null) {
            volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (isBound && musicService != null) {
                        // MediaPlayer volume range is 0.0f to 1.0f
                        float volume = progress / 100f;
                        musicService.setVolume(volume);
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // --- Social Media Button Placeholders ---

        View.OnClickListener socialPlaceholder = v ->
                Toast.makeText(MainActivity.this, "Feature coming soon: Opening external link...", Toast.LENGTH_SHORT).show();

        findViewById(R.id.socialButton1).setOnClickListener(socialPlaceholder);
        findViewById(R.id.socialButton2).setOnClickListener(socialPlaceholder);
        findViewById(R.id.socialButton3).setOnClickListener(socialPlaceholder);
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