package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAccessGuardTest {

    @Test
    void rejectsWhenOperatorKeyIsNotConfigured() {
        OperatorAccessGuard guard = new OperatorAccessGuard("");

        assertThatThrownBy(() -> guard.requireAccess("anything"))
                .isInstanceOf(BusinessBaseException.class);
    }

    @Test
    void usesExactConfiguredSecret() {
        OperatorAccessGuard guard = new OperatorAccessGuard("long-random-operator-key");

        assertThatThrownBy(() -> guard.requireAccess("wrong"))
                .isInstanceOf(BusinessBaseException.class);
        assertThatCode(() -> guard.requireAccess("long-random-operator-key"))
                .doesNotThrowAnyException();
    }
}
