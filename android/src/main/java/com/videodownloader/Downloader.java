package com.videodownloader;

import android.content.Context;
import android.media.MediaMetadata;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.videodownloader.license.OfflineLicenseManager;
import com.videodownloader.offline.AxDownloadTracker;
import com.videodownloader.util.Utility;

import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.common.MediaItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Downloader {
  private OfflineLicenseManager mLicenseManager;
  private AxDownloadTracker mAxDownloadTracker;
  // A helper for initializing and removing downloads
  private DownloadHelper mDownloadHelper;

  private boolean isNetworkAvailable(Context context) {
    ConnectivityManager connectivityManager
      = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetworkInfo = null;
    if (connectivityManager != null) {
      activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    }
    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  private void downloadLicense(MediaItem mediaItem) {
    MediaItem.DrmConfiguration drmConfiguration = Utility.getDrmConfiguration(mediaItem);
    if (drmConfiguration != null) {
      mLicenseManager.downloadLicense(
        String.valueOf(drmConfiguration.licenseUri),
        String.valueOf(Utility.getPlaybackProperties(mediaItem).uri),
        drmConfiguration.licenseRequestHeaders.get("X-AxDRM-Message"));
    }
  }

}
