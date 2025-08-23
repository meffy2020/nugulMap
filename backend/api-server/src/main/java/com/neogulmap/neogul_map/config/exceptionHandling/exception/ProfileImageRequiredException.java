package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

public class ProfileImageRequiredException extends BusinessBaseException {
    public ProfileImageRequiredException() {
        super(ErrorCode.PROFILE_IMAGE_REQUIRED);
    }
}
