package com.example.dingdingjumper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.Random;

public class DingDingAccessibilityService extends AccessibilityService {

    private static final String TAG = "DingDingService";
    private static final String DINGTALK_PACKAGE_NAME = "com.alibaba.android.rimet";
    public static final String ACTION_PERFORM_CLOCK = "com.example.dingdingjumper.PERFORM_CLOCK";
    public static final String ACTION_CHECK_IN_ALARM = "com.example.dingdingjumper.CHECK_IN_ALARM";
    public static final String ACTION_CHECK_OUT_ALARM = "com.example.dingdingjumper.CHECK_OUT_ALARM";

    private static final String CHANNEL_ID = "DingDingAccessibilityChannel";
    private static final int NOTIFICATION_ID = 100;

    private static final int RETURN_TO_APP_DELAY = 13000; // 延长返回应用的延迟到13秒

    private Handler handler;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private String currentOperation = "";
    private boolean isProcessing = false;
    private boolean receiverRegistered = false;
    private Random random = new Random();

    private BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                Log.d(TAG, "接收到广播: " + action);
                if (ACTION_PERFORM_CLOCK.equals(action)) {
                    final String type = intent.getStringExtra("type");
                    final int delaySeconds = intent.getIntExtra("delaySeconds", 0);
                    final boolean isTest = intent.getBooleanExtra("isTest", false);

                    if (delaySeconds > 0) {
                        // 计算实际延迟秒数（0到delaySeconds之间的随机值）
                        final int actualDelay = isTest ? random.nextInt(delaySeconds + 1) : 0;

                        // 显示延迟通知
                        showNotification("准备执行打卡",
                                type + "打卡将在" + actualDelay + "秒后执行");

                        try {
                            Toast.makeText(DingDingAccessibilityService.this,
                                    type + "打卡将在" + actualDelay + "秒后执行",
                                    Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e(TAG, "显示Toast失败", e);
                        }

                        // 延迟执行
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        performClock(type != null ? type : "未知");
                                    }
                                });
                            }
                        }, actualDelay * 1000L); // 转换为毫秒
                    } else {
                        // 无需延迟，直接执行
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                performClock(type != null ? type : "未知");
                            }
                        });
                    }
                } else if (ACTION_CHECK_IN_ALARM.equals(action)) {
                    // 这些由AlarmReceiver处理
                    Log.d(TAG, "收到上班闹钟，已由AlarmReceiver处理");
                } else if (ACTION_CHECK_OUT_ALARM.equals(action)) {
                    Log.d(TAG, "收到下班闹钟，已由AlarmReceiver处理");
                }
            } catch (Exception e) {
                Log.e(TAG, "广播接收器发生异常", e);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "服务onCreate");

        // 初始化Handler，确保在主线程上
        handler = new Handler(Looper.getMainLooper());

        // 创建通知通道
        createNotificationChannel();

        // 获取通知管理器
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 获取唤醒锁
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DingDingJumper:AccessibilityWakeLock");

        try {
            // 注册广播接收器
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_PERFORM_CLOCK);
            filter.addAction(ACTION_CHECK_IN_ALARM);
            filter.addAction(ACTION_CHECK_OUT_ALARM);
            registerReceiver(clockReceiver, filter);
            receiverRegistered = true;
            Log.d(TAG, "广播接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "注册广播接收器失败", e);
        }

        // 显示通知
        showNotification("钉钉跳转器服务运行中", "自动打卡服务已启动，等待打卡时间到达");

        // 显示Toast表示服务已启动
        try {
            Toast.makeText(this, "钉钉跳转器辅助服务已启动", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "显示Toast失败", e);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "服务onDestroy");

        try {
            // 取消所有延迟任务
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }

            // 注销广播接收器
            if (receiverRegistered) {
                unregisterReceiver(clockReceiver);
                receiverRegistered = false;
                Log.d(TAG, "广播接收器注销成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "注销广播接收器失败", e);
        }

        // 释放唤醒锁
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "释放唤醒锁失败", e);
        }

        // 移除通知
        try {
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception e) {
            Log.e(TAG, "取消通知失败", e);
        }

        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 在这里可以处理辅助功能事件
    }

    @Override
    public void onInterrupt() {
        // 服务中断
        Log.d(TAG, "服务中断");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "服务连接成功");

        // 服务连接时的设置
        try {
            Toast.makeText(this, "钉钉跳转器辅助服务已启动", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "显示Toast失败", e);
        }
    }

    private void performClock(final String type) {
        Log.d(TAG, "执行打卡操作: " + type);

        // 防止重复处理
        if (isProcessing) {
            Log.d(TAG, "当前已有任务正在执行: " + currentOperation);
            showNotification("操作进行中", "当前已有" + currentOperation + "打卡任务正在执行，请稍后");
            return;
        }

        isProcessing = true;
        currentOperation = type;

        // 获取唤醒锁，确保操作完成
        try {
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10*60*1000L); // 10分钟
            }
        } catch (Exception e) {
            Log.e(TAG, "获取唤醒锁失败", e);
        }

        // 显示通知
        showNotification("开始执行打卡", type + "打卡任务开始执行");

        // 启动钉钉
        if (isAppInstalled(DINGTALK_PACKAGE_NAME)) {
            try {
                Log.d(TAG, "启动钉钉应用");
                launchDingTalk();

                // 更新通知
                showNotification("打卡中", "正在执行" + type + "打卡，将在5秒后返回");

                // 延迟后跳回应用
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(TAG, "尝试返回应用");

                            // 方法1：使用MainActivity的类直接启动
                            Intent launchIntent = new Intent(DingDingAccessibilityService.this, MainActivity.class);
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            launchIntent.putExtra("from_service", true);
                            launchIntent.putExtra("timestamp", System.currentTimeMillis()); // 添加时间戳避免intent被系统视为重复

                            // 延长检查应用返回成功的时间
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    startActivity(launchIntent);
                                    Log.d(TAG, "成功返回应用 - 方法1");

                                    // 延长完成任务的时间
                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            finishTask(type, true);
                                        }
                                    }, 2000); // 延长到2秒
                                }
                            }, 500); // 先延迟500ms再启动应用

                        } catch (Exception e1) {
                            Log.e(TAG, "方法1返回应用失败，尝试方法2", e1);

                            try {
                                // 方法2：使用应用启动器启动
                                PackageManager pm = getPackageManager();
                                Intent launchIntent = pm.getLaunchIntentForPackage(getPackageName());
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    launchIntent.putExtra("from_service", true);
                                    launchIntent.putExtra("timestamp", System.currentTimeMillis());

                                    handler.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            startActivity(launchIntent);
                                            Log.d(TAG, "成功返回应用 - 方法2");

                                            // 延长完成任务的时间
                                            handler.postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    finishTask(type, true);
                                                }
                                            }, 2000);
                                        }
                                    }, 500);
                                } else {
                                    Log.e(TAG, "获取应用启动Intent失败");
                                    finishTask(type, false);
                                }
                            } catch (Exception e2) {
                                Log.e(TAG, "返回应用方法2也失败", e2);
                                finishTask(type, false);
                            }
                        }
                    }
                }, RETURN_TO_APP_DELAY);
            } catch (Exception e) {
                Log.e(TAG, "启动钉钉失败", e);
                showNotification("操作失败", "启动钉钉失败: " + e.getMessage());
                finishTask(type, false);
            }
        } else {
            // 钉钉未安装
            Log.e(TAG, "钉钉应用未安装");
            showNotification("操作失败", "未安装钉钉应用，请先安装");
            try {
                Toast.makeText(this, "未安装钉钉应用，请先安装", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "显示Toast失败", e);
            }

            finishTask(type, false);
        }
    }

    private void finishTask(final String type, boolean success) {
        try {
            // 显示通知
            String notificationText = success
                    ? type + "打卡已完成并返回应用"
                    : type + "打卡已完成但返回应用失败";

            showNotification("打卡完成", notificationText);

            // 显示Toast
            try {
                String toastText = success
                        ? type + "打卡已完成"
                        : type + "打卡已完成但返回应用失败";

                Toast.makeText(DingDingAccessibilityService.this,
                        toastText, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "显示Toast失败", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "完成任务时发生异常", e);
        } finally {
            // 延迟释放资源
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (wakeLock != null && wakeLock.isHeld()) {
                            wakeLock.release();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "释放唤醒锁失败", e);
                    }

                    isProcessing = false;
                }
            }, 3000); // 延迟3秒释放
        }
    }

    private void launchDingTalk() {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(DINGTALK_PACKAGE_NAME);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                Log.d(TAG, "钉钉应用已启动");
            } else {
                Log.e(TAG, "获取钉钉启动Intent失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "启动钉钉时发生异常", e);
            throw e; // 重新抛出异常以便上层处理
        }
    }

    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "钉钉跳转器通知",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("用于钉钉跳转器辅助服务");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "创建通知通道失败", e);
            }
        }
    }

    private void showNotification(String title, String text) {
        try {
            if (notificationManager == null) {
                Log.e(TAG, "通知管理器为空");
                return;
            }

            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_launcher_foreground) // 确保有此图标资源
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            Notification notification = builder.build();
            notificationManager.notify(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "显示通知失败", e);
        }
    }
}