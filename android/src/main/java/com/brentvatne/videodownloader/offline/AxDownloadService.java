package com.brentvatne.videodownloader.offline;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.NotificationUtil;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadNotificationHelper;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.PlatformScheduler;

//import com.google.android.exoplayer2.offline.Download;
//import com.google.android.exoplayer2.offline.DownloadManager;
//import com.google.android.exoplayer2.offline.DownloadService;
//import com.google.android.exoplayer2.scheduler.PlatformScheduler;
//import com.google.android.exoplayer2.offline.DownloadNotificationHelper;
//import com.google.android.exoplayer2.util.NotificationUtil;
//import com.google.android.exoplayer2.util.Util;

import com.brentvatne.react.R;

import java.util.List;

@UnstableApi
/**
 * A class that extends Exoplayer's DownloadService class.
 * Defines a service that enables the downloads to continue even when the
 * app is in background.
 */
public class AxDownloadService extends DownloadService {

    private static final String CHANNEL_ID = "download_channel";
    private static final int JOB_ID = 1;
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    public static final String NOTIFICATION = "com.videodownloader.offline.AxDownloadService";
    public static final String PROGRESS = "progress";
    public static final String MEDIA_URL = "media_url";

    // Helper for creating a download notifications
    private DownloadNotificationHelper notificationHelper;

    public @OptIn(markerClass = UnstableApi.class) AxDownloadService() {
        super(FOREGROUND_NOTIFICATION_ID,
                DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
                CHANNEL_ID,
                R.string.exo_download_notification_channel_name,
                0);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new DownloadNotificationHelper(this, CHANNEL_ID);
        DownloadManager downloadManager = AxOfflineManager.getInstance().getDownloadManager();
        if (downloadManager != null) {
            downloadManager.addListener(
                    new TerminalStateNotificationHelper(
                            this, notificationHelper, FOREGROUND_NOTIFICATION_ID + 1));
        }
    }

    @NonNull
    @Override
    protected DownloadManager getDownloadManager() {
        return AxOfflineManager.getInstance().getDownloadManager();
    }

    @Override
    protected PlatformScheduler getScheduler() {
        return Util.SDK_INT >= 21 ? new PlatformScheduler(this, JOB_ID) : null;
    }

    // Returns a notification to be displayed
    @Override
    protected Notification getForegroundNotification(List<Download> downloads, int i) {
        Notification notification = notificationHelper.buildProgressNotification(this,
                R.drawable.ic_download, null, null, downloads, i);
        if (notification.extras != null) {
          // Notification about download progress is sent here
          try {
            Download download = downloads.size() > i ? downloads.get(i) : null;
            String mediaURL = download != null ? download.request.uri.toString() : null;
            sendNotification(notification.extras.getInt(Notification.EXTRA_PROGRESS), mediaURL);
          } catch (Exception e) {
            Log.e(AxDownloadService.class.getSimpleName(), "Failed to extract download progress", e);
          }
        }
        return notification;
    }

    // A method that sends a notification
    private void sendNotification(int progress, String mediaUrl) {
        Intent intent = new Intent(NOTIFICATION);
        intent.putExtra(PROGRESS, progress);
        intent.putExtra(MEDIA_URL, mediaUrl);
        sendBroadcast(intent);
    }

    // For listening download changes and sending notifications about it
    private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {

        private final Context context;
        private final DownloadNotificationHelper notificationHelper;

        private int nextNotificationId;

        public TerminalStateNotificationHelper(
                Context context, DownloadNotificationHelper notificationHelper, int firstNotificationId) {
            this.context = context.getApplicationContext();
            this.notificationHelper = notificationHelper;
            nextNotificationId = firstNotificationId;
        }

        @OptIn(markerClass = UnstableApi.class)
        @Override
        public void  onDownloadChanged(DownloadManager manager, Download download, Exception exception) {
            Notification notification;
            if (download.state == Download.STATE_COMPLETED) {
                notification =
                        notificationHelper.buildDownloadCompletedNotification(context,
                                R.drawable.ic_download_done,
                                /* contentIntent= */ null,
                                Util.fromUtf8Bytes(download.request.data));
            } else if (download.state == Download.STATE_FAILED) {
                notification =
                        notificationHelper.buildDownloadFailedNotification(context,
                                R.drawable.ic_download_done,
                                /* contentIntent= */ null,
                                Util.fromUtf8Bytes(download.request.data));
            } else {
                return;
            }
            NotificationUtil.setNotification(context, nextNotificationId++, notification);
        }
    }
}
