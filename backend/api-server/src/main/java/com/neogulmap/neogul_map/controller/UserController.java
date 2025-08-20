package com.neogulmap.neogul_map.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import java.lang.Long;

import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("id") Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 조회 성공")
                .body(UserResponse.from(user));
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserRequest userRequest) {
        UserResponse userResponse = userService.createUser(userRequest);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 생성 성공")
                .body(userResponse);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable("id") Long id, @RequestBody @Valid UserRequest userRequest) {
        UserResponse userResponse = userService.updateUser(id, userRequest);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 업데이트 성공")
                .body(userResponse);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 삭제 성공")
                .build();
    }
}
