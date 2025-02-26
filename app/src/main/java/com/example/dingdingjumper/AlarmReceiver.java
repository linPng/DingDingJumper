package com.example.dingdingjumper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Random;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String PREF_NAME = "DingDingJumperPrefs";
    private static final String PREF_DELAY_SECONDS = "delaySeconds";

    private Random random = new Random();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "接收到闹钟广播: " + action);

        // 确保设备唤醒
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;

        try {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "DingDingJumper:AlarmWakeLock");
            wakeLock.acquire(10*60*1000L); // 10分钟

            // 获取延迟设置
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            int maxDelaySeconds = prefs.getInt(PREF_DELAY_SECONDS, 60);

            // 生成随机延迟秒数（0到maxDelaySeconds之间）
            final int delaySeconds = random.nextInt(maxDelaySeconds + 1);

            // 根据不同的闹钟类型设置操作类型
            final String clockType;
            if (action != null) {
                if (action.equals(MainActivity.ACTION_CHECK_IN_ALARM)) {
                    clockType = "上班";
                    try {
                        Toast.makeText(context, "上班打卡时间到，将在" + delaySeconds + "秒后执行", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "显示Toast失败", e);
                    }
                } else if (action.equals(MainActivity.ACTION_CHECK_OUT_ALARM)) {
                    clockType = "下班";
                    try {
                        Toast.makeText(context, "下班打卡时间到，将在" + delaySeconds + "秒后执行", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "显示Toast失败", e);
                    }
                } else {
                    // 未知操作类型
                    clockType = null;
                }

                // 如果有有效的操作类型，延迟执行
                if (clockType != null) {
                    // 创建处理器进行延迟
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent serviceIntent = new Intent(DingDingAccessibilityService.ACTION_PERFORM_CLOCK);
                            serviceIntent.putExtra("type", clockType);
                            serviceIntent.putExtra("delaySeconds", 0); // 已经延迟过，不需要再延迟

                            Log.d(TAG, "延迟" + delaySeconds + "秒后发送广播: " + serviceIntent.getAction());
                            context.sendBroadcast(serviceIntent);

                            try {
                                Toast.makeText(context, clockType + "打卡开始执行", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e(TAG, "显示Toast失败", e);
                            }
                        }
                    }, delaySeconds * 1000L); // 转换为毫秒
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理闹钟广播时发生异常", e);
        } finally {
            // 不立即释放唤醒锁，让延迟执行的任务有机会完成
            // 唤醒锁会在10分钟后自动释放，或者由AccessibilityService接手
        }
    }
}