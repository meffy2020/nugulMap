package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

public class ProfileImageProcessingException extends BusinessBaseException {
    public ProfileImageProcessingException() {
        super(ErrorCode.PROFILE_IMAGE_PROCESSING_ERROR);
    }
    
    public ProfileImageProcessingException(String message) {
        super(ErrorCode.PROFILE_IMAGE_PROCESSING_ERROR, message);
    }
}
