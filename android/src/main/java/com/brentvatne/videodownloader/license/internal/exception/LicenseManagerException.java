package com.brentvatne.videodownloader.license.internal.exception;

import com.brentvatne.videodownloader.license.LicenseManagerErrorCode;

/**
 * Drm message Exception
 */
public class LicenseManagerException extends Exception {
    private final LicenseManagerErrorCode mErrorCode;
    private final String mExtraData;

    public LicenseManagerException(LicenseManagerErrorCode errorCode) {
        this(errorCode, null);
    }

    public LicenseManagerException(LicenseManagerErrorCode errorCode, String extraData) {
        super();
        mErrorCode = errorCode;
        mExtraData = extraData;
    }

    public LicenseManagerErrorCode getErrorCode() {
        return mErrorCode;
    }

    public String getExtraData() {
        return mExtraData;
    }
}
