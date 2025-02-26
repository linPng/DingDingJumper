package com.example.dingdingjumper;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "DingDingJumperPrefs";
    private static final String PREF_CHECK_IN_HOUR = "checkInHour";
    private static final String PREF_CHECK_IN_MINUTE = "checkInMinute";
    private static final String PREF_CHECK_OUT_HOUR = "checkOutHour";
    private static final String PREF_CHECK_OUT_MINUTE = "checkOutMinute";
    private static final String PREF_ALARM_ENABLED = "alarmEnabled";
    private static final String PREF_DELAY_SECONDS = "delaySeconds";

    public static final String ACTION_CHECK_IN_ALARM = "com.example.dingdingjumper.CHECK_IN_ALARM";
    public static final String ACTION_CHECK_OUT_ALARM = "com.example.dingdingjumper.CHECK_OUT_ALARM";
    private static final int REQUEST_ACCESSIBILITY = 1000;

    private TextView checkInTimeText;
    private TextView checkOutTimeText;
    private Button setCheckInTimeButton;
    private Button setCheckOutTimeButton;
    private Switch enableAlarmSwitch;
    private Button testJumpButton;
    private TextView statusTextView;
    private Button accessibilitySettingsButton;
    private SeekBar delaySeekBar;
    private TextView delayValueText;

    private int checkInHour = 9;
    private int checkInMinute = 0;
    private int checkOutHour = 18;
    private int checkOutMinute = 0;
    private boolean alarmEnabled = false;
    private int delaySeconds = 60; // 默认延迟60秒

    private AlarmManager alarmManager;
    private PendingIntent checkInPendingIntent;
    private PendingIntent checkOutPendingIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化视图
        initViews();

        // 加载保存的设置
        loadSettings();

        // 检查是否是从服务跳转回来的
        if (getIntent().getBooleanExtra("from_service", false)) {
            Log.d("MainActivity", "从服务跳转回来，时间戳：" + getIntent().getLongExtra("timestamp", 0));
            // 使用 post 确保界面完全初始化后再执行
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateStatus(alarmEnabled ? "状态: 定时任务已启用，刚完成一次打卡操作" : "状态: 定时任务已禁用，刚完成一次打卡操作");
                    // 可以在这里添加一些特殊处理
                }
            }, 1000);
        }

        // 更新UI显示
        updateTimeDisplay();
        updateDelayDisplay();

        // 初始化闹钟管理器和PendingIntent
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent checkInIntent = new Intent(ACTION_CHECK_IN_ALARM);
        checkInPendingIntent = PendingIntent.getBroadcast(
                this, 1, checkInIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent checkOutIntent = new Intent(ACTION_CHECK_OUT_ALARM);
        checkOutPendingIntent = PendingIntent.getBroadcast(
                this, 2, checkOutIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 如果启用了闹钟，设置闹钟
        if (alarmEnabled) {
            setAlarms();
        }

        // 检查无障碍服务是否已启用
        checkAccessibilityServiceEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到应用时检查无障碍服务状态
        checkAccessibilityServiceEnabled();
    }

    private void initViews() {
        checkInTimeText = findViewById(R.id.checkInTimeText);
        checkOutTimeText = findViewById(R.id.checkOutTimeText);
        setCheckInTimeButton = findViewById(R.id.setCheckInTimeButton);
        setCheckOutTimeButton = findViewById(R.id.setCheckOutTimeButton);
        enableAlarmSwitch = findViewById(R.id.enableAlarmSwitch);
        testJumpButton = findViewById(R.id.testJumpButton);
        statusTextView = findViewById(R.id.statusTextView);
        accessibilitySettingsButton = findViewById(R.id.accessibilitySettingsButton);
        delaySeekBar = findViewById(R.id.delaySeekBar);
        delayValueText = findViewById(R.id.delayValueText);

        // 设置上班时间按钮点击事件
        setCheckInTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(true);
            }
        });

        // 设置下班时间按钮点击事件
        setCheckOutTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePickerDialog(false);
            }
        });

        // 设置启用开关状态变化事件
        enableAlarmSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && !isAccessibilityServiceEnabled()) {
                    Toast.makeText(MainActivity.this, "请先启用无障碍服务", Toast.LENGTH_LONG).show();
                    enableAlarmSwitch.setChecked(false);
                    return;
                }

                alarmEnabled = isChecked;
                saveSettings();

                if (isChecked) {
                    setAlarms();
                    updateStatus("状态: 定时任务已启用");
                } else {
                    cancelAlarms();
                    updateStatus("状态: 定时任务已禁用");
                }
            }
        });

        // 设置延迟滑块事件
        delaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                delaySeconds = progress;
                updateDelayDisplay();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 不需要处理
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();

                // 如果闹钟已启用，提示用户设置已更新
                if (alarmEnabled) {
                    Toast.makeText(MainActivity.this, "延迟设置已更新", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置测试跳转按钮点击事件
        testJumpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(MainActivity.this, "请先启用无障碍服务", Toast.LENGTH_LONG).show();
                    return;
                }

                // 发送测试打卡广播
                Intent intent = new Intent(DingDingAccessibilityService.ACTION_PERFORM_CLOCK);
                intent.putExtra("type", "test");
                intent.putExtra("delaySeconds", delaySeconds);
                intent.putExtra("isTest", true); // 添加测试标记
                sendBroadcast(intent);

                Toast.makeText(MainActivity.this, "测试打卡指令已发送，将在0-" + delaySeconds + "秒内随机执行", Toast.LENGTH_SHORT).show();
            }
        });

        // 设置无障碍服务设置按钮
        accessibilitySettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivityForResult(intent, REQUEST_ACCESSIBILITY);
            }
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    this.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }

        if (accessibilityEnabled == 1) {
            String servicesValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

            if (servicesValue != null) {
                return servicesValue.contains(getPackageName() + "/" + DingDingAccessibilityService.class.getName());
            }
        }

        return false;
    }

    private void checkAccessibilityServiceEnabled() {
        boolean enabled = isAccessibilityServiceEnabled();
        accessibilitySettingsButton.setText(enabled ? "无障碍服务已启用" : "启用无障碍服务");
        accessibilitySettingsButton.setBackgroundColor(enabled ?
                getResources().getColor(android.R.color.holo_green_dark) :
                getResources().getColor(android.R.color.holo_red_light));

        // 如果服务未启用但开关开启，关闭开关
        if (!enabled && enableAlarmSwitch.isChecked()) {
            enableAlarmSwitch.setChecked(false);
            alarmEnabled = false;
            saveSettings();
            updateStatus("状态: 无障碍服务未启用，定时任务已禁用");
        }
    }

    private void showTimePickerDialog(final boolean isCheckIn) {
        int hour = isCheckIn ? checkInHour : checkOutHour;
        int minute = isCheckIn ? checkInMinute : checkOutMinute;

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        if (isCheckIn) {
                            checkInHour = hourOfDay;
                            checkInMinute = minute;
                        } else {
                            checkOutHour = hourOfDay;
                            checkOutMinute = minute;
                        }

                        updateTimeDisplay();
                        saveSettings();

                        // 如果闹钟已启用，重新设置闹钟
                        if (alarmEnabled) {
                            setAlarms();
                        }
                    }
                },
                hour,
                minute,
                true
        );

        timePickerDialog.setTitle(isCheckIn ? "设置上班时间" : "设置下班时间");
        timePickerDialog.show();
    }

    private void updateTimeDisplay() {
        checkInTimeText.setText(String.format("%02d:%02d", checkInHour, checkInMinute));
        checkOutTimeText.setText(String.format("%02d:%02d", checkOutHour, checkOutMinute));
    }

    private void updateDelayDisplay() {
        delayValueText.setText(String.format("%d秒", delaySeconds));
    }

    private void updateStatus(String status) {
        statusTextView.setText(status);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        checkInHour = prefs.getInt(PREF_CHECK_IN_HOUR, 9);
        checkInMinute = prefs.getInt(PREF_CHECK_IN_MINUTE, 0);
        checkOutHour = prefs.getInt(PREF_CHECK_OUT_HOUR, 18);
        checkOutMinute = prefs.getInt(PREF_CHECK_OUT_MINUTE, 0);
        alarmEnabled = prefs.getBoolean(PREF_ALARM_ENABLED, false);
        delaySeconds = prefs.getInt(PREF_DELAY_SECONDS, 60); // 加载延迟时间设置

        delaySeekBar.setProgress(delaySeconds);
        enableAlarmSwitch.setChecked(alarmEnabled);
        updateStatus(alarmEnabled ? "状态: 定时任务已启用" : "状态: 定时任务已禁用");
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putInt(PREF_CHECK_IN_HOUR, checkInHour);
        editor.putInt(PREF_CHECK_IN_MINUTE, checkInMinute);
        editor.putInt(PREF_CHECK_OUT_HOUR, checkOutHour);
        editor.putInt(PREF_CHECK_OUT_MINUTE, checkOutMinute);
        editor.putBoolean(PREF_ALARM_ENABLED, alarmEnabled);
        editor.putInt(PREF_DELAY_SECONDS, delaySeconds); // 保存延迟时间设置
        editor.apply();
    }

    @SuppressLint("ScheduleExactAlarm")
    private void setAlarms() {
        // 取消之前的闹钟
        cancelAlarms();

        // 设置今天的上班时间
        Calendar checkInCalendar = Calendar.getInstance();
        checkInCalendar.set(Calendar.HOUR_OF_DAY, checkInHour);
        checkInCalendar.set(Calendar.MINUTE, checkInMinute);
        checkInCalendar.set(Calendar.SECOND, 0);

        // 如果当前时间已经过了今天的上班时间，设置为明天
        if (checkInCalendar.getTimeInMillis() < System.currentTimeMillis()) {
            checkInCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 设置今天的下班时间
        Calendar checkOutCalendar = Calendar.getInstance();
        checkOutCalendar.set(Calendar.HOUR_OF_DAY, checkOutHour);
        checkOutCalendar.set(Calendar.MINUTE, checkOutMinute);
        checkOutCalendar.set(Calendar.SECOND, 0);

        // 如果当前时间已经过了今天的下班时间，设置为明天
        if (checkOutCalendar.getTimeInMillis() < System.currentTimeMillis()) {
            checkOutCalendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 设置重复的闹钟
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    checkInCalendar.getTimeInMillis(), checkInPendingIntent);
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    checkOutCalendar.getTimeInMillis(), checkOutPendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                    checkInCalendar.getTimeInMillis(), checkInPendingIntent);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                    checkOutCalendar.getTimeInMillis(), checkOutPendingIntent);
        }

        // 更新状态
        String nextCheckIn = String.format("%02d:%02d", checkInCalendar.get(Calendar.HOUR_OF_DAY),
                checkInCalendar.get(Calendar.MINUTE));
        String nextCheckOut = String.format("%02d:%02d", checkOutCalendar.get(Calendar.HOUR_OF_DAY),
                checkOutCalendar.get(Calendar.MINUTE));

        updateStatus("状态: 已设置 - 下次上班打卡: " + nextCheckIn + ", 下次下班打卡: " + nextCheckOut);

        Toast.makeText(this, "定时任务已设置，将在设定时间后随机延迟0-" + delaySeconds + "秒内执行", Toast.LENGTH_SHORT).show();
    }

    private void cancelAlarms() {
        if (alarmManager != null) {
            alarmManager.cancel(checkInPendingIntent);
            alarmManager.cancel(checkOutPendingIntent);
        }
    }
}