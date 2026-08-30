package com.stoumpos.wave;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

public class RadioAlarmActivity extends AppCompatActivity {

    private TimePicker timePicker;
    private Switch enabledSwitch;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_radio_alarm);

        timePicker = findViewById(R.id.alarmTimePicker);
        enabledSwitch = findViewById(R.id.alarmEnabledSwitch);
        statusText = findViewById(R.id.alarmStatusText);
        Button saveButton = findViewById(R.id.alarmSaveButton);

        timePicker.setIs24HourView(true);
        timePicker.setHour(AlarmScheduler.getHour(this));
        timePicker.setMinute(AlarmScheduler.getMinute(this));
        enabledSwitch.setChecked(AlarmScheduler.isEnabled(this));
        updateStatusText();

        saveButton.setOnClickListener(v -> {
            if (enabledSwitch.isChecked()) {
                AlarmScheduler.enable(this, timePicker.getHour(), timePicker.getMinute());
            } else {
                AlarmScheduler.disable(this);
            }
            updateStatusText();
        });
    }

    private void updateStatusText() {
        if (AlarmScheduler.isEnabled(this)) {
            statusText.setText(getString(
                    R.string.radio_alarm_status_on,
                    AlarmScheduler.getHour(this),
                    AlarmScheduler.getMinute(this)));
        } else {
            statusText.setText(R.string.radio_alarm_status_off);
        }
    }
}
