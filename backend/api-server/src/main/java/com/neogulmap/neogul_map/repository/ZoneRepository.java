package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.Zone;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ZoneRepository extends JpaRepository<Zone, Integer>, JpaSpecificationExecutor<Zone> {
    Optional<Zone> findByAddress(String address);
    
    // 키워드로 검색 (지역, 주소, 타입, 서브타입에서 검색)
    @Query("SELECT z FROM Zone z WHERE " +
           "LOWER(z.region) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.type) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.subtype) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Zone> findByKeyword(@Param("keyword") String keyword);
    
    // 키워드로 검색 (페이지네이션 지원)
    @Query("SELECT z FROM Zone z WHERE " +
           "LOWER(z.region) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.type) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.subtype) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Zone> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    // 지역별 검색
    List<Zone> findByRegionContainingIgnoreCase(String region);
    Page<Zone> findByRegionContainingIgnoreCase(String region, Pageable pageable);
    
    // 타입별 검색
    List<Zone> findByTypeContainingIgnoreCase(String type);
    Page<Zone> findByTypeContainingIgnoreCase(String type, Pageable pageable);
    
    // 서브타입별 검색
    List<Zone> findBySubtypeContainingIgnoreCase(String subtype);
    Page<Zone> findBySubtypeContainingIgnoreCase(String subtype, Pageable pageable);
    
    // 크기별 검색
    List<Zone> findBySizeContainingIgnoreCase(String size);
    Page<Zone> findBySizeContainingIgnoreCase(String size, Pageable pageable);
    
    // 사용자별 검색
    List<Zone> findByUserContainingIgnoreCase(String user);
    Page<Zone> findByUserContainingIgnoreCase(String user, Pageable pageable);
    
    // 복합 검색 (지역 + 타입)
    List<Zone> findByRegionContainingIgnoreCaseAndTypeContainingIgnoreCase(String region, String type);
    Page<Zone> findByRegionContainingIgnoreCaseAndTypeContainingIgnoreCase(String region, String type, Pageable pageable);
    
    // 복합 검색 (지역 + 서브타입)
    List<Zone> findByRegionContainingIgnoreCaseAndSubtypeContainingIgnoreCase(String region, String subtype);
    Page<Zone> findByRegionContainingIgnoreCaseAndSubtypeContainingIgnoreCase(String region, String subtype, Pageable pageable);
    
    // 위치 기반 검색 - Haversine 공식을 사용한 반경 내 검색
    @Query("SELECT z FROM Zone z WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(z.latitude)) * " +
           "cos(radians(z.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(z.latitude)))) <= :radiusKm")
    List<Zone> findNearbyZones(@Param("latitude") Double latitude, 
                              @Param("longitude") Double longitude, 
                              @Param("radiusKm") Double radiusKm);
    
    // 위치 기반 검색 + 타입 필터링
    @Query("SELECT z FROM Zone z WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(z.latitude)) * " +
           "cos(radians(z.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(z.latitude)))) <= :radiusKm " +
           "AND LOWER(z.type) LIKE LOWER(CONCAT('%', :type, '%'))")
    List<Zone> findNearbyZonesByType(@Param("latitude") Double latitude, 
                                    @Param("longitude") Double longitude, 
                                    @Param("radiusKm") Double radiusKm,
                                    @Param("type") String type);
    
    // 위치 기반 검색 + 지역 필터링
    @Query("SELECT z FROM Zone z WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(z.latitude)) * " +
           "cos(radians(z.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(z.latitude)))) <= :radiusKm " +
           "AND LOWER(z.region) LIKE LOWER(CONCAT('%', :region, '%'))")
    List<Zone> findNearbyZonesByRegion(@Param("latitude") Double latitude, 
                                      @Param("longitude") Double longitude, 
                                      @Param("radiusKm") Double radiusKm,
                                      @Param("region") String region);
    
    // 주소로 검색 (MVP용)
    List<Zone> findByAddressContainingIgnoreCase(String address);
}
