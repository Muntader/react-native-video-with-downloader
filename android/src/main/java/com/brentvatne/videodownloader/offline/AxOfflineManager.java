package com.brentvatne.videodownloader.offline;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.DatabaseProvider;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.offline.DownloadManager;

import java.io.File;
import java.util.concurrent.Executors;

@UnstableApi
/**
 * A class that manages the initialization of DownloadManager and data source factory objects.
 */
public class AxOfflineManager {

    private static final String TAG = AxOfflineManager.class.getSimpleName();
    private static AxOfflineManager sAxOfflineManager;
    private DatabaseProvider databaseProvider;
    private File mDownloadDirectory;
    private DownloadManager mDownloadManager;
    private AxDownloadTracker mDownloadTracker;
    private Cache mDownloadCache;

    // Return and create the AxOfflineManager instance if necessary
    public static AxOfflineManager getInstance() {
        if (sAxOfflineManager == null) {
            sAxOfflineManager = new AxOfflineManager();
        }
        return sAxOfflineManager;
    }

    public AxDownloadTracker getDownloadTracker() {
        return mDownloadTracker;
    }

    public DownloadManager getDownloadManager() {
        return mDownloadManager;
    }

    public void init(Context context) {
        File downloadsFolder = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        init (context, downloadsFolder);
    }

    // Initializing of AxOfflineManager

    @OptIn(markerClass = UnstableApi.class)
    public synchronized void init(Context context, File folder) {
        Log.d(TAG, "init() called with: context = [" + context + "], folder = [" + folder + "]");
        if (mDownloadManager == null) {
            mDownloadDirectory = folder;
            mDownloadManager = new DownloadManager(
                    context.getApplicationContext(),
                    getDatabaseProvider(context),
                    getDownloadCache(context),
                    buildHttpDataSourceFactory(),
                    Executors.newFixedThreadPool(6));
            mDownloadTracker = new AxDownloadTracker(context, buildDataSourceFactory(context),
                    mDownloadManager);
        }
    }

    private File getDownloadDirectory(Context context) {
        if (mDownloadDirectory == null) {
            mDownloadDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            Log.d(TAG, "Setting value to mDownloadDirectory: " + mDownloadDirectory);
        }
        return mDownloadDirectory;
    }

    private synchronized Cache getDownloadCache(Context context) {
        if (mDownloadCache == null) {
            mDownloadCache = new SimpleCache(getDownloadDirectory(context), new NoOpCacheEvictor(),
                    getDatabaseProvider(context));
        }
        return mDownloadCache;
    }

    // Returns a {@link DataSource.Factory}
    public DataSource.Factory buildDataSourceFactory(Context context) {
        DefaultDataSource.Factory upstreamFactory =
                new DefaultDataSource.Factory(context, buildHttpDataSourceFactory());
        return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
    }

    public DataSource.Factory buildCacheWrappedDataSourceFactory(Context context, DataSource.Factory upstreamFactory) {
        return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(context));
    }

    // Returns a {@link HttpDataSource.Factory}
    private HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSource.Factory();
    }

    private static CacheDataSource.Factory buildReadOnlyCacheDataSource(
            DataSource.Factory upstreamFactory, Cache cache) {
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private DatabaseProvider getDatabaseProvider(Context context) {
        if (databaseProvider == null) {
            databaseProvider = new StandaloneDatabaseProvider(context.getApplicationContext());
        }
        return databaseProvider;
    }
}
