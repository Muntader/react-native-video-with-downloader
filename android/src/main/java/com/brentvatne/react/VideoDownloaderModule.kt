package com.brentvatne.react

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.brentvatne.videodownloader.license.OfflineLicenseManager
import com.brentvatne.videodownloader.offline.AxDownloadService
import com.brentvatne.videodownloader.offline.AxDownloadTracker
import com.brentvatne.videodownloader.offline.AxOfflineManager
import com.brentvatne.videodownloader.util.Utility
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.io.IOException


class VideoDownloaderModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), AxDownloadTracker.Listener {

    companion object {
        private const val REACT_CLASS = "VideoDownloader"
        private const val DOWNLOAD_STATUS_EVENT = "video-downloader.download-status"
        private const val DOWNLOAD_PROGRESS_EVENT = "video-downloader.download-progress"
        private const val TAG = REACT_CLASS
    }

    override fun getName(): String = REACT_CLASS

    private val mLicenseManager: OfflineLicenseManager = OfflineLicenseManager(reactContext)
    private var mAxDownloadTracker: AxDownloadTracker? = null
    private var mDownloadHelper: DownloadHelper? = null
    private val pendingDownloadPromiseMap = mutableMapOf<String, Promise>()

    init {
        AxOfflineManager.getInstance().init(reactContext)
    }

    @ReactMethod
    fun startDownload(url: String, title: String, drm: ReadableMap?, promise: Promise) {
        val mediaItemBuilder = MediaItem.Builder().apply {
            setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            setUri(url)
            drm?.let {
                val drmUuid = drm.getString("type")?.let { it1 -> Util.getDrmUuid(it1) }
                val drmConfigBuilder = drmUuid?.let { it1 ->
                    MediaItem.DrmConfiguration.Builder(it1).apply {
                        drm.getString("licenseServer")?.let { setLicenseUri(it) }
                        drm.getString("licenseToken")?.let { token ->
                            setLicenseRequestHeaders(mapOf("X-AxDRM-Message" to token))
                        }
                    }
                }
                if (drmConfigBuilder != null) {
                    setDrmConfiguration(drmConfigBuilder.build())
                }
            }
        }

        val mediaItem = mediaItemBuilder.build()
        downloadLicenseWithResult(mediaItem)
        initOfflineManager()

        mDownloadHelper?.release()
        mAxDownloadTracker?.clearDownloadHelper()

        mDownloadHelper = mAxDownloadTracker?.getDownloadHelper(mediaItem, reactApplicationContext)
        try {
            mDownloadHelper?.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper) {
                    val tracks = getTracks()
                    mAxDownloadTracker?.download(mediaItem.mediaId, tracks)
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    Log.e(TAG, "Failed to start download", e)
                    promise.reject("Download failed, exception: ", e)
                }
            })
        } catch (e: Exception) {
            promise.reject("Download failed, exception: ", e)
        }

        pendingDownloadPromiseMap[url] = promise
    }

    @ReactMethod
    fun removeDownload(url: String?, promise: Promise) {
        try {
            ensureOfflineManagerInitialized()

            onRemoveLicense(url!!)

            val downloadRequest: DownloadRequest? = mAxDownloadTracker!!.getDownloadRequest(Uri.parse(url))

            if (downloadRequest != null) {
                // In the case of failed download, this will be null.

                DownloadService.sendRemoveDownload(
                    reactApplicationContext, AxDownloadService::class.java, downloadRequest.id, false
                )
            }

            promise.resolve(url)
        } catch (e: java.lang.Exception) {
            promise.reject("Could not remove download", e)
        }
    }

    @ReactMethod
    fun removeAllDownloads(promise: Promise) {
        try {
            ensureOfflineManagerInitialized()

            onRemoveAllLicenses()

            DownloadService.sendRemoveAllDownloads(reactApplicationContext, AxDownloadService::class.java, false)

            promise.resolve("success")
        } catch (e: java.lang.Exception) {
            promise.reject("Could not remove all downloads", e)
        }
    }

    private fun ensureOfflineManagerInitialized() {
        if (mAxDownloadTracker == null) {
            initOfflineManager()
        }
    }

    @ReactMethod
    fun getDownloadStatus(url: String?, promise: Promise) {
        try {
            ensureOfflineManagerInitialized()

            val downloaded = mAxDownloadTracker!!.isDownloaded(url)

            promise.resolve(downloaded)
        } catch (e: java.lang.Exception) {
            promise.reject("Could not get download status", e)
        }
    }


    @SuppressLint("NewApi")
    private fun initOfflineManager() {
        if (mAxDownloadTracker == null) {
            mAxDownloadTracker = AxOfflineManager.getInstance().downloadTracker
        }
        mAxDownloadTracker?.addListener(this)
        val serviceClass = AxDownloadService::class.java

        try {
            DownloadService.start(reactApplicationContext, serviceClass)
        } catch (e: IllegalStateException) {
            DownloadService.startForeground(reactApplicationContext, serviceClass)
        }

        val intentFilter = IntentFilter(AxDownloadService.NOTIFICATION)
        val receiverFlag =
            if (Build.VERSION.SDK_INT >= 34) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED

        reactApplicationContext.registerReceiver(mBroadcastReceiver, intentFilter, receiverFlag)
    }

    private fun downloadLicenseWithResult(mediaItem: MediaItem) {
        val drmConfiguration = Utility.getDrmConfiguration(mediaItem)
        drmConfiguration?.let {
            mLicenseManager.downloadLicenseWithResult(
                it.licenseUri.toString(),
                Utility.getPlaybackProperties(mediaItem).uri.toString(),
                it.licenseRequestHeaders["X-AxDRM-Message"],
                true
            )
        }
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val progress = intent.getIntExtra(AxDownloadService.PROGRESS, 0)
            val mediaUrl = intent.getStringExtra(AxDownloadService.MEDIA_URL)

            val params = Arguments.createMap().apply {
                putString("url", mediaUrl)
                putInt("progress", progress)
            }
            sendEvent(DOWNLOAD_PROGRESS_EVENT, params)
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun getTracks(): Array<IntArray> {
        val tracks = ArrayList<IntArray>()

        for (period in 0 until mDownloadHelper!!.periodCount) {
            val mappedTrackInfo = mDownloadHelper!!.getMappedTrackInfo(period)

            for (renderer in 0 until mappedTrackInfo.rendererCount) {
                val trackType = mappedTrackInfo.getRendererType(renderer)
                val trackGroupArray = mappedTrackInfo.getTrackGroups(renderer)

                // Handle video tracks
                if (trackType == C.TRACK_TYPE_VIDEO) {
                    var selectedVideoTrackIndexes: IntArray? = null
                    var bestResolution = Int.MAX_VALUE // Start with maximum possible resolution
                    for (group in 0 until trackGroupArray.length) {
                        val trackGroup = trackGroupArray[group]
                        for (track in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(track)
                            if (format.height <= 720 && format.height < bestResolution) {
                                bestResolution = format.height
                                selectedVideoTrackIndexes = intArrayOf(period, renderer, group, track)
                            }
                        }
                    }
                    if (selectedVideoTrackIndexes != null) {
                        tracks.add(selectedVideoTrackIndexes)
                    }
                }

                // Handle audio tracks
                if (trackType == C.TRACK_TYPE_AUDIO) {
                    for (group in 0 until trackGroupArray.length) {
                        val trackGroup = trackGroupArray[group]
                        for (track in 0 until trackGroup.length) {
                            tracks.add(intArrayOf(period, renderer, group, track))
                        }
                    }
                }

                // Handle subtitle tracks
                if (trackType == C.TRACK_TYPE_TEXT) {
                    for (group in 0 until trackGroupArray.length) {
                        val trackGroup = trackGroupArray[group]
                        for (track in 0 until trackGroup.length) {
                            tracks.add(intArrayOf(period, renderer, group, track))
                        }
                    }
                }
            }
        }

        val tracksToDownload = Array(tracks.size) { IntArray(1) }
        for (i in tracks.indices) {
            tracksToDownload[i] = tracks[i]
        }

        for (row in tracksToDownload) {
            Log.d(TAG, "Tracks to download: " + row.contentToString())
        }

        return tracksToDownload
    }


    private fun onRemoveAllLicenses() {
        mLicenseManager.releaseAllLicenses()
    }

    private fun onRemoveLicense(url: String) {
        mLicenseManager.releaseLicense(url)
    }


    // A method for checking whether network is available
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = currentActivity!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var activeNetworkInfo: NetworkInfo? = null

        if (connectivityManager != null) {
            activeNetworkInfo = connectivityManager.activeNetworkInfo
        }

        return activeNetworkInfo != null && activeNetworkInfo.isConnected
    }

    override fun onDownloadsChanged(mediaUrl: String, state: Int) {
        when (state) {
            Download.STATE_DOWNLOADING -> Log.d(TAG, "Download state: Downloading")

            Download.STATE_COMPLETED -> {
                Log.d(TAG, "Download state: Downloaded")

                val pendingPromise = pendingDownloadPromiseMap[mediaUrl]

                if (pendingPromise != null) {
                    pendingPromise.resolve(mediaUrl)

                    pendingDownloadPromiseMap.remove(mediaUrl)
                }
            }

            Download.STATE_QUEUED -> Log.d(TAG, "Download state: Queued")

            Download.STATE_FAILED -> {
                Log.d(TAG, "Download state: Failed")

                val pendingPromise = pendingDownloadPromiseMap[mediaUrl]

                pendingPromise!!.reject(Error("Failed to download $mediaUrl"))

                pendingDownloadPromiseMap.remove(mediaUrl)
            }

            Download.STATE_STOPPED -> Log.d(TAG, "Download state: Stopped")

            else -> Log.d(TAG, "onDownload State changed : other:$state")

        }
    }

    @ReactMethod
    fun addListener(eventName: String) {}

    @ReactMethod
    fun removeListeners(count: Int) {}

}
