package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.User;

final class PublicUserLabel {

    private static final String ANONYMOUS = "익명사용자";

    private PublicUserLabel() {
    }

    static String from(User user) {
        if (user == null || user.getNickname() == null) {
            return ANONYMOUS;
        }

        String nickname = user.getNickname().trim();
        if (nickname.isEmpty() || nickname.contains("@")) {
            return ANONYMOUS;
        }
        return nickname;
    }
}
