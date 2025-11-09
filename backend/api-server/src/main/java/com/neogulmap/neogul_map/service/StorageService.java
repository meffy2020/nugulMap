package com.neogulmap.neogul_map.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장을 위한 서비스 인터페이스
 * 트랜잭션과 분리하여 고아 파일 문제를 방지
 */
public interface StorageService {
    
    /**
     * 임시 파일로 저장 (트랜잭션 커밋 전)
     * @param file 업로드할 파일
     * @return 임시 파일명
     */
    String saveTemp(MultipartFile file);
    
    /**
     * 임시 파일을 최종 파일로 확정 (트랜잭션 커밋 후)
     * @param tempName 임시 파일명
     * @param finalName 최종 파일명
     */
    void confirm(String tempName, String finalName);
    
    /**
     * 파일 삭제 (조용히 실패 허용)
     * @param fileName 삭제할 파일명
     */
    void deleteQuietly(String fileName);
    
    /**
     * 파일이 존재하는지 확인
     * @param fileName 확인할 파일명
     * @return 파일 존재 여부
     */
    boolean exists(String fileName);
}


