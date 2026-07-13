package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class ReviewContentPolicy {

    private final List<String> normalizedBlockedTerms;

    public ReviewContentPolicy(
            @Value("${app.moderation.blocked-terms:씨발,시발,개새끼,병신,좆,fuck}") String blockedTerms) {
        normalizedBlockedTerms = Arrays.stream(blockedTerms.split(","))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .map(ReviewContentPolicy::normalizeForMatching)
                .distinct()
                .toList();
    }

    public void ensureAllowed(String content) {
        String normalizedContent = normalizeForMatching(content);
        boolean containsBlockedTerm = normalizedBlockedTerms.stream()
                .anyMatch(normalizedContent::contains);
        if (containsBlockedTerm) {
            throw new ValidationException(
                    ErrorCode.REVIEW_CONTENT_REJECTED,
                    "공격적이거나 부적절한 표현은 등록할 수 없습니다."
            );
        }
    }

    private static String normalizeForMatching(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
