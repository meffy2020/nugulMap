package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageControllerUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageService imageService;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("PublicUrlBuilder가 만든 /api/images/{filename} 경로는 ImageController가 제공한다")
    void generatedImageUrlMatchesImageControllerRoute() throws Exception {
        when(imageService.getImage("sample.jpg")).thenReturn(new ByteArrayResource("image".getBytes()));
        when(imageService.getContentType("sample.jpg")).thenReturn("image/jpeg");

        mockMvc.perform(get("/api/images/sample.jpg").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Image-Filename", "sample.jpg"));
    }
}
