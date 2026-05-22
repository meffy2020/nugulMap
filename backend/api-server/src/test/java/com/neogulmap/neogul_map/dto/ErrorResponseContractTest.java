package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseContractTest {

    @Test
    @DisplayName("에러 응답은 success=false 계약을 항상 포함한다")
    void errorResponseIncludesSuccessFalse() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.VALIDATION_ERROR);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("V001");
        assertThat(response.getStatus()).isEqualTo(400);
    }
}
