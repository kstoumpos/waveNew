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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private MusicService musicService;
    private boolean isBound = false;
    private TextView loadingStatus;
    private final String currentUrl = "https://sp1.32bit.gr/8018/;";
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

        findViewById(R.id.pauseButton).setOnClickListener(v -> {
            if (isBound) {
                musicService.stop();
                loadingStatus.setVisibility(View.INVISIBLE);
                Toast.makeText(this, "Stream Stopped", Toast.LENGTH_SHORT).show();
            }
        });

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