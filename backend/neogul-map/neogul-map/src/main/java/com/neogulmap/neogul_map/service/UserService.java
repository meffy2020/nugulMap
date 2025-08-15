package com.neogulmap.neogul_map.service;

import org.springframework.stereotype.Service;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        User user = User.builder()
                .email(userRequest.getEmail())
                .oauthId(userRequest.getOauthId())
                .oauthProvider(userRequest.getOauthProvider())
                .nickname(userRequest.getNickname())
                .profileImage(userRequest.getProfileImage())
                .createdAt(userRequest.getCreatedAt())
                .build();
        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        user.update(userRequest);
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }

}
