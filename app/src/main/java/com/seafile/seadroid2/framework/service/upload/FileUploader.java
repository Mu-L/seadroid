package com.seafile.seadroid2.framework.service.upload;

import android.content.Context;
import android.text.TextUtils;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.SupportAccountManager;
import com.seafile.seadroid2.enums.FeatureDataSource;
import com.seafile.seadroid2.framework.notification.TransferNotificationDispatcher;
import com.seafile.seadroid2.framework.service.ParentEventUploader;
import com.seafile.seadroid2.framework.util.SafeLogs;
import com.seafile.seadroid2.framework.util.Toasts;
import com.seafile.seadroid2.framework.worker.GlobalTransferCacheList;
import com.seafile.seadroid2.framework.worker.TransferEvent;
import com.seafile.seadroid2.framework.worker.queue.TransferModel;

public class FileUploader extends ParentEventUploader {
    private final String TAG = "FileUploader";

    public FileUploader(Context context, TransferNotificationDispatcher transferNotificationDispatcher) {
        super(context, transferNotificationDispatcher);
    }

    @Override
    public FeatureDataSource getFeatureDataSource() {
        return FeatureDataSource.MANUAL_FILE_UPLOAD;
    }

    public void stopById(String modelId) {
        SafeLogs.d(TAG, "stopById()", "stop download by id: " + modelId);

        TransferModel transferModel = getCurrentTransferringModel();
        if (transferModel == null) {
            return;
        }

        if (!TextUtils.equals(modelId, transferModel.getId())) {
            GlobalTransferCacheList.FILE_UPLOAD_QUEUE.remove(modelId);
            return;
        }

        //stop
        stopThis();
    }

    public SeafException upload() {
        SafeLogs.d(TAG, "startUpload()", "start upload");
        //send a start event
        send(FeatureDataSource.MANUAL_FILE_UPLOAD, TransferEvent.EVENT_TRANSFER_TASK_START);

        int totalPendingCount = GlobalTransferCacheList.FILE_UPLOAD_QUEUE.getPendingCount();
        if (totalPendingCount <= 0) {
            return returnSuccess();
        }

        SafeLogs.d(TAG, "startUpload()", "pending count: " + totalPendingCount);

        SeafException resultException = SeafException.SUCCESS;
        SeafException interruptException = SeafException.SUCCESS;
        SeafException lastException = SeafException.SUCCESS;

        while (true) {
            TransferModel transferModel = GlobalTransferCacheList.FILE_UPLOAD_QUEUE.pick();
            if (transferModel == null) {
                break;
            }

            try {
                Account specialAccount = SupportAccountManager.getInstance().getSpecialAccount(transferModel.related_account);
                if (specialAccount == null) {
                    SafeLogs.d(TAG, "startUpload()", "special account is null: " + transferModel.related_account);
                    continue;
                }

                transfer(specialAccount, transferModel);

            } catch (SeafException seafException) {

                SafeLogs.e(TAG, seafException.getMessage());

                // In some cases, the transmission needs to be interrupted
                boolean isInterrupt = isInterrupt(seafException);
                if (isInterrupt) {
                    SafeLogs.e("An exception occurred and the transmission has been interrupted");
                    notifyError(seafException);

                    interruptException = seafException;
                    break;
                } else {
                    lastException = seafException;

                    SafeLogs.e("An exception occurred and the next transfer will continue");
                }
            }
        }

        if (interruptException != SeafException.SUCCESS) {
            resultException = interruptException;
            SafeLogs.d(TAG, "all completed", "error msg:[interruptException]: " + resultException.getMessage());

        } else if (totalPendingCount == 1 && lastException != SeafException.SUCCESS) {
            resultException = lastException;
            SafeLogs.d(TAG, "all completed", "error msg:[lastException]: " + resultException.getMessage());
        } else {
            SafeLogs.d(TAG, "all completed");
        }

        String errorMsg = null;
        if (resultException != SeafException.SUCCESS) {
            errorMsg = resultException.getMessage();
            Toasts.show(R.string.upload_failed);
        } else {
            Toasts.show(R.string.upload_completed);
        }

        sendCompleteEvent(FeatureDataSource.MANUAL_FILE_UPLOAD, errorMsg, totalPendingCount);
        return resultException;
    }

    protected SeafException returnSuccess() {
        send(FeatureDataSource.MANUAL_FILE_UPLOAD, TransferEvent.EVENT_TRANSFER_TASK_COMPLETE);
        return SeafException.SUCCESS;
    }

}
