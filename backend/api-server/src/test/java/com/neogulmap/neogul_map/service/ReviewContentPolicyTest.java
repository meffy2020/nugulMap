package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewContentPolicyTest {

    private final ReviewContentPolicy policy = new ReviewContentPolicy("씨발,개새끼,병신,fuck");

    @Test
    void rejectsCaseAndPunctuationObfuscation() {
        assertThatThrownBy(() -> policy.ensureAllowed("F.U C-K 같은 욕설"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void allowsOrdinaryLocationFeedback() {
        assertThatCode(() -> policy.ensureAllowed("환기가 잘 되고 관리 상태가 깨끗해요."))
                .doesNotThrowAnyException();
    }
}
