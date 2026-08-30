package com.stoumpos.wave;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Resolves the radio stream URL from a small remote JSON config file, caching
 * the result in SharedPreferences so the stream URL can be changed later
 * without an app update.
 */
public class StreamConfig {

    private static final String TAG = "StreamConfig";
    private static final String CONFIG_URL =
            "https://raw.githubusercontent.com/kstoumpos/waveStramUrl/main/config.json";
    private static final String PREFS_NAME = "stream_config";
    private static final String KEY_STREAM_URL = "stream_url";
    private static final String DEFAULT_STREAM_URL = "https://sp1.32bit.gr/8018/;";

    private StreamConfig() {
    }

    /** Returns the last known-good stream URL, falling back to a hardcoded default. */
    public static String getCachedUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_STREAM_URL, DEFAULT_STREAM_URL);
    }

    /** Fetches the remote config in the background and updates the cache on success. */
    public static void refresh(Context context) {
        Context appContext = context.getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            String fetchedUrl = fetchStreamUrl();
            if (fetchedUrl != null && !fetchedUrl.isEmpty()) {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_STREAM_URL, fetchedUrl)
                        .apply();
            }
        });
    }

    private static String fetchStreamUrl() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");

            StringBuilder builder = new StringBuilder();
            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }

            JSONObject json = new JSONObject(builder.toString());
            return json.optString("streamUrl", null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch remote stream config", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
