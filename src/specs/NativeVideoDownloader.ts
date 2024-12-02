import {NativeModules } from 'react-native';
import { UnsafeObject } from 'react-native/Libraries/Types/CodegenTypes';

export enum DrmType {
  WIDEVINE = 'widevine',
  PLAYREADY = 'playready',
  CLEARKEY = 'clearkey',
  FAIRPLAY = 'fairplay',
}

export type DRM = {
  type: DrmType;
  licenseServer: string;
  certificateUrl?: string;
  licenseToken: string;
  url: string;
};
export type DownloadResumableCallback = (progress: number) => void;
export type DownloadVideoType = {
  url: string;
  title: string;
  drm?: DRM;
  callback?: DownloadResumableCallback;
};

export interface VideoDownloaderType {
  startDownload: (url: string, title: string, drm?: UnsafeObject) => Promise<void>;
  removeDownload: (url: string) => Promise<void>;
  removeAllDownloads: () => Promise<void>;
  getDownloadStatus: (url: string) => Promise<boolean>;
}

export default NativeModules.VideoDownloader as VideoDownloaderType;
