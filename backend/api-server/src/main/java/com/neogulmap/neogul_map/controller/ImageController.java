package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 이미지 조회
     */
    @GetMapping("/{filename}")
    public ResponseEntity<?> getImage(@PathVariable String filename) {
        Resource resource = imageService.getImage(filename);
        String contentType = imageService.getContentType(filename);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))  
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .header("X-Image-Filename", filename)
                .body(resource);
    }

}
