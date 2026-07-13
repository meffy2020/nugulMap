package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.User;

/**
 * 로컬 계정 삭제 전에 외부 OAuth 제공자 연결 해제를 시도하는 경계입니다.
 * 제공자 장애처럼 사용자가 해결할 수 없는 실패는 호출자가 수동 해제 신호로 전환할 수 있습니다.
 */
public interface LinkedAccountRevocationService {
    void revokeBeforeDeletion(User user);
}
