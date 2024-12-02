import { NativeEventEmitter, NativeModules} from 'react-native';
import NativeVideoDownloaderModule, {VideoDownloaderType, DRM, DownloadResumableCallback} from './specs/NativeVideoDownloader';


// Bind the Native Module
const VideoDownloaderNative = NativeModules.VideoDownloader as VideoDownloaderType;

// Event emitter for progress updates
const VideoDownloaderEventEmitter = new NativeEventEmitter(NativeModules.VideoDownloader);

// Event name for progress updates
const VIDEO_PROGRESS_EVENT = 'video-downloader.download-progress';

export type DownloadVideoType = {
  url: string;
  title: string;
  drm?: DRM;
  callback?: DownloadResumableCallback;
};

// Class to handle resumable downloads
export class DownloadResumable {
  private url: string;
  private title: string;
  private drm?: DRM;
  private callback?: DownloadResumableCallback;
  private subscription: any;

  constructor({ url, title, drm, callback }: DownloadVideoType) {
    this.url = url;
    this.title = title;
    this.drm = drm;
    this.callback = callback;
  }

  public async startDownloadAsync() {
    // Listen to progress updates
    this.subscription = VideoDownloaderEventEmitter.addListener(
      VIDEO_PROGRESS_EVENT,
      ({ progress, url }) => {
        if (this.callback && this.url === url) {
          this.callback(progress);
        }
      }
    );

    try {
      await VideoDownloaderNative.startDownload(this.url, this.title, this.drm);
    } finally {
      if (this.subscription) {
        this.subscription.remove();
      }
    }
  }

  public async stopDownloadAsync() {
    // Additional functionality for stopping downloads can be added here
  }
}

// Utility Functions

/**
 * Create a resumable download
 * @param {DownloadVideoType} params - Parameters for the download
 * @returns {DownloadResumable}
 */
export function createDownloadResumable(params: DownloadVideoType): DownloadResumable {
  return new DownloadResumable(params);
}

/**
 * Remove a specific download
 * @param {string} url - URL of the video to remove
 * @returns {Promise<void>}
 */
export async function removeDownloadAsync(url: string): Promise<void> {
  return VideoDownloaderNative.removeDownload(url);
}

/**
 * Remove all downloads
 * @returns {Promise<void>}
 */
export async function removeAllDownloadsAsync(): Promise<void> {
  return VideoDownloaderNative.removeAllDownloads();
}

/**
 * Get the download status of a video
 * @param {string} url - URL of the video
 * @returns {Promise<boolean>}
 */
export async function getDownloadStatusAsync(url: string): Promise<boolean> {
  return VideoDownloaderNative.getDownloadStatus(url);
}


export default {
  createDownloadResumable,
  removeDownloadAsync,
  removeAllDownloadsAsync,
  getDownloadStatusAsync,
};
