package com.edu.edumeet.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/** 브랜치 보호가 실제로 막는지 확인하는 일회용 시험. 확인 뒤 지운다. */
@DisplayName("보호 확인용")
class ProtectionProbeTest {
    @Test
    @DisplayName("반드시 실패한다")
    void 반드시_실패한다() {
        fail("브랜치 보호가 이 PR 을 막아야 한다");
    }
}
