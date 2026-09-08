package com.neogulmap.neogul_map.domain.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Converter
public class RecommendationTagSetConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        Collection<String> tags = attribute == null ? Set.of() : attribute;
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("추천 태그를 저장할 수 없습니다.", exception);
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(OBJECT_MAPPER.readValue(dbData, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("저장된 추천 태그를 읽을 수 없습니다.", exception);
        }
    }
}
