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
    
    // 키워드로 검색 + 거리순 정렬 (Haversine 공식 적용)
    @Query("SELECT z, (6371 * acos(cos(radians(:latitude)) * cos(radians(z.latitude)) * " +
           "cos(radians(z.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(z.latitude)))) AS distance " +
           "FROM Zone z WHERE " +
           "LOWER(z.region) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.type) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.subtype) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY distance ASC")
    List<Object[]> findByKeywordOrderByDistance(@Param("keyword") String keyword, 
                                               @Param("latitude") Double latitude, 
                                               @Param("longitude") Double longitude);

    @Query("SELECT z FROM Zone z WHERE " +
           "LOWER(z.region) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.type) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(z.subtype) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Zone> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    List<Zone> findByRegionContainingIgnoreCase(String region);
    Page<Zone> findByRegionContainingIgnoreCase(String region, Pageable pageable);
    List<Zone> findByTypeContainingIgnoreCase(String type);
    Page<Zone> findByTypeContainingIgnoreCase(String type, Pageable pageable);
    List<Zone> findBySubtypeContainingIgnoreCase(String subtype);
    Page<Zone> findBySubtypeContainingIgnoreCase(String subtype, Pageable pageable);
    List<Zone> findBySizeContainingIgnoreCase(String size);
    Page<Zone> findBySizeContainingIgnoreCase(String size, Pageable pageable);
    List<Zone> findByUserContainingIgnoreCase(String user);
    Page<Zone> findByUserContainingIgnoreCase(String user, Pageable pageable);
    List<Zone> findByCreatorId(Long creatorId);
    Page<Zone> findByCreatorId(Long creatorId, Pageable pageable);
    
    @Query("SELECT z FROM Zone z JOIN FETCH z.creator WHERE z.creator.id = :creatorId")
    List<Zone> findByCreatorIdWithCreator(@Param("creatorId") Long creatorId);
    
    List<Zone> findByRegionContainingIgnoreCaseAndTypeContainingIgnoreCase(String region, String type);
    Page<Zone> findByRegionContainingIgnoreCaseAndTypeContainingIgnoreCase(String region, String type, Pageable pageable);
    List<Zone> findByRegionContainingIgnoreCaseAndSubtypeContainingIgnoreCase(String region, String subtype);
    Page<Zone> findByRegionContainingIgnoreCaseAndSubtypeContainingIgnoreCase(String region, String subtype, Pageable pageable);
    
    @Query("SELECT z FROM Zone z WHERE " +
           "(6371 * acos(cos(radians(:latitude)) * cos(radians(z.latitude)) * " +
           "cos(radians(z.longitude) - radians(:longitude)) + " +
           "sin(radians(:latitude)) * sin(radians(z.latitude)))) <= :radiusKm")
    List<Zone> findNearbyZones(@Param("latitude") Double latitude, 
                              @Param("longitude") Double longitude, 
                              @Param("radiusKm") Double radiusKm);

    @Query("SELECT z FROM Zone z WHERE " +
           "z.latitude BETWEEN :minLat AND :maxLat AND " +
           "z.longitude BETWEEN :minLng AND :maxLng")
    List<Zone> findByLocationBounds(@Param("minLat") Double minLat, 
                                   @Param("maxLat") Double maxLat, 
                                   @Param("minLng") Double minLng, 
                                   @Param("maxLng") Double maxLng);
    
    List<Zone> findByAddressContainingIgnoreCase(String address);
}