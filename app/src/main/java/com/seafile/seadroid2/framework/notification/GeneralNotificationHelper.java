package com.seafile.seadroid2.framework.notification;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.StringRes;

import com.seafile.seadroid2.framework.notification.base.BaseNotification;
import com.seafile.seadroid2.framework.notification.base.NotificationUtils;

public class GeneralNotificationHelper extends BaseNotification {
    @Override
    public String getChannelId() {
        return NotificationUtils.NOTIFICATION_CHANNEL_GENERAL;
    }

    @Override
    public int getMaxProgress() {
        return 0;
    }

    public GeneralNotificationHelper(Context context) {
        super(context);
    }

    ////////////////
    /// general
    ////////////////
    public void showNotification(@StringRes int titleRes) {
        super.showNotification(NotificationUtils.NOTIFICATION_GENERAL_ID, context.getString(titleRes), null, null);
    }

    public void showNotification(String title) {
        super.showNotification(NotificationUtils.NOTIFICATION_GENERAL_ID, title, null, null);
    }

    public void showNotification(@StringRes int titleRes, @StringRes int contentRes) {
        super.showNotification(NotificationUtils.NOTIFICATION_GENERAL_ID, context.getString(titleRes), context.getString(contentRes), null);
    }

    public void showNotification(String title, String content) {
        super.showNotification(NotificationUtils.NOTIFICATION_GENERAL_ID, title, content, null);
    }

    public void showNotification(@StringRes int titleRes, @StringRes int contentRes, Intent intent) {
        super.showNotification(NotificationUtils.NOTIFICATION_GENERAL_ID, context.getString(titleRes), context.getString(contentRes), intent);
    }

    ////////////////
    /// error
    ////////////////
    public void showErrorNotification(@StringRes int titleRes) {
        super.showNotification(NotificationUtils.NOTIFICATION_ERROR_ID, context.getString(titleRes), null, null);
    }

    public void showErrorNotification(String title) {
        super.showNotification(NotificationUtils.NOTIFICATION_ERROR_ID, title, null, null);
    }

    public void showErrorNotification(@StringRes int titleRes, @StringRes int contentRes) {
        super.showNotification(NotificationUtils.NOTIFICATION_ERROR_ID, context.getString(titleRes), context.getString(contentRes), null);
    }

    public void showErrorNotification(String title, String content) {
        super.showNotification(NotificationUtils.NOTIFICATION_ERROR_ID, title, content, null);
    }

    public void showErrorNotification(@StringRes int titleRes, @StringRes int contentRes, Intent intent) {
        super.showNotification(NotificationUtils.NOTIFICATION_ERROR_ID, context.getString(titleRes), context.getString(contentRes), intent);
    }
}
