package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.repository.UserRepository;
import com.fijalkoa.biosso.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class UserInfoController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.extractEmail(token);

        return userRepository.findByEmail(email)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
                        "sub", user.getId(),
                        "email", user.getEmail()
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("error", "invalid_token")));
    }
}

