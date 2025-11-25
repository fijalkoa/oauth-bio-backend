package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.biometric.WsClientApp;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/biometric")
public class BiometricController {

    private final String WS_SERVER_URL = "ws://localhost:5000/ws";

    @PostMapping("/register")
    public String registerUser(
            @RequestParam("user_id") String userId,
            @RequestParam("image") MultipartFile image) throws Exception {

        WsClientApp wsClient = new WsClientApp();
        wsClient.connectAndSendImage(WS_SERVER_URL, image.getBytes());

        return "Image sent for user: " + userId;
    }
}
