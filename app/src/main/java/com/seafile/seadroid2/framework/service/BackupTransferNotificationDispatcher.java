package com.seafile.seadroid2.framework.service;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static com.seafile.seadroid2.framework.notification.base.NotificationUtils.NOTIFICATION_MESSAGE_KEY;
import static com.seafile.seadroid2.framework.notification.base.NotificationUtils.NOTIFICATION_OPEN_DOWNLOAD_TAB;
import static com.seafile.seadroid2.framework.notification.base.NotificationUtils.NOTIFICATION_OPEN_UPLOAD_TAB;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.enums.FeatureDataSource;
import com.seafile.seadroid2.framework.notification.base.NotificationUtils;
import com.seafile.seadroid2.ui.transfer_list.TransferActivity;

import java.util.HashMap;
import java.util.Map;

public class BackupTransferNotificationDispatcher implements ITransferNotification {

    private final int REQ_CODE = 1;
    private final Context context;
    private final NotificationManager manager;

    public BackupTransferNotificationDispatcher(Context context) {
        this.context = context;
        this.manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private static final Map<FeatureDataSource, Integer> NOTIFY_IDS = new HashMap<>() {{
        put(FeatureDataSource.ALBUM_BACKUP, NotificationUtils.NID_TRANSFER_UPLOAD_ALBUM_BACKUP);
        put(FeatureDataSource.FOLDER_BACKUP, NotificationUtils.NID_UPLOAD_FOLDER);
        put(FeatureDataSource.DOWNLOAD, NotificationUtils.NID_DOWNLOAD);
        put(FeatureDataSource.MANUAL_FILE_UPLOAD, NotificationUtils.NID_UPLOAD_FILE);
        put(FeatureDataSource.SHARE_FILE_TO_SEAFILE, NotificationUtils.NID_UPLOAD_FILE);
        put(FeatureDataSource.AUTO_UPDATE_LOCAL_FILE, NotificationUtils.NID_UPLOAD_FILE);
    }};

    public Integer getNotifyId(FeatureDataSource source) {
        if (source == null) {
            throw new IllegalArgumentException("FeatureDataSource cannot be null");
        }

        return NOTIFY_IDS.getOrDefault(source, NotificationUtils.NID_GENERAL);
    }

    private String getDefaultTitle(FeatureDataSource source) {
        if (source == null) {
            throw new IllegalArgumentException("FeatureDataSource cannot be null");
        }

        return switch (source) {
            case ALBUM_BACKUP -> context.getString(R.string.settings_camera_upload_info_title);
            case FOLDER_BACKUP -> context.getString(R.string.settings_folder_backup_info_title);
            case MANUAL_FILE_UPLOAD, SHARE_FILE_TO_SEAFILE, AUTO_UPDATE_LOCAL_FILE ->
                    context.getString(R.string.channel_name_upload);
            case DOWNLOAD -> context.getString(R.string.download);
        };
    }

    private Intent getDefaultPendingIntent(FeatureDataSource source) {
        if (source == null) {
            throw new IllegalArgumentException("FeatureDataSource cannot be null");
        }

        Intent dIntent = null;
        switch (source) {
            case ALBUM_BACKUP: {
                dIntent = new Intent(context, TransferActivity.class);
                dIntent.putExtra(NOTIFICATION_MESSAGE_KEY, NOTIFICATION_OPEN_UPLOAD_TAB);
                dIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            case FOLDER_BACKUP: {
                dIntent = new Intent(context, TransferActivity.class);
                dIntent.putExtra(NOTIFICATION_MESSAGE_KEY, NOTIFICATION_OPEN_UPLOAD_TAB);
                dIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            case MANUAL_FILE_UPLOAD: {
                dIntent = new Intent(context, TransferActivity.class);
                dIntent.putExtra(NOTIFICATION_MESSAGE_KEY, NOTIFICATION_OPEN_UPLOAD_TAB);
                dIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            case DOWNLOAD: {
                dIntent = new Intent(context, TransferActivity.class);
                dIntent.putExtra(NOTIFICATION_MESSAGE_KEY, NOTIFICATION_OPEN_DOWNLOAD_TAB);
                dIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
        }

        return dIntent;
    }

    @Override
    public void showNotification(FeatureDataSource source, String subTitle) {
        String title = getDefaultTitle(source);

        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_TRANSFER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(subTitle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .build();

        int nid = getNotifyId(source);
        manager.notify(nid, notification);
    }

    private final Map<String, Long> intervalMap = new HashMap<>();

    @Override
    public void showProgress(FeatureDataSource source, String fileName, int percent) {

        if (TextUtils.isEmpty(fileName)) {
            return;
        }

        Long sourceTs = intervalMap.getOrDefault(source.name(), 0L);
        long now = System.currentTimeMillis();
        if (now - sourceTs < 1000) {
            return;
        }
        intervalMap.put(source.name(), now);

        String progressStr = context.getString(R.string.notification_upload_upload_in_progress);
        String title = getDefaultTitle(source);
        String content = String.format(progressStr, percent, title);

        //
        PendingIntent pendingIntent = null;
        Intent intent = getDefaultPendingIntent(source);
        if (intent != null) {
            pendingIntent = PendingIntent.getActivity(context,
                    REQ_CODE,
                    intent,
                    FLAG_IMMUTABLE);
        }


        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_TRANSFER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(fileName)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setProgress(100, percent, false)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();

        int nid = getNotifyId(source);
        manager.notify(nid, notification);
    }

    @Override
    public void showCompleted(FeatureDataSource source) {
        String title = getDefaultTitle(source);
        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_TRANSFER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setTimeoutAfter(3000)
                .build();

        int nid = getNotifyId(source);
        manager.notify(nid, notification);
    }

    @Override
    public void showError(FeatureDataSource source) {
        String title = getDefaultTitle(source);
        Notification notification = new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_TRANSFER)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setAutoCancel(true)
                .build();

        int nid = getNotifyId(source);
        manager.notify(nid, notification);
    }

    @Override
    public void clearAll() {
        clearAll(0);
    }

    @Override
    public void clearLater(FeatureDataSource source) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            manager.cancel(getNotifyId(source));
        }, 3000);
    }

    @Override
    public void clearAll(long delayMillis) {
        if (delayMillis > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                for (Integer id : NOTIFY_IDS.values()) {
                    manager.cancel(id);
                }
            }, delayMillis);
        } else {
            for (Integer id : NOTIFY_IDS.values()) {
                manager.cancel(id);
            }
        }
    }
}
