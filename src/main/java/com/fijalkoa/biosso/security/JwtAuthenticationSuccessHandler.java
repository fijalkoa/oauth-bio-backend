package com.fijalkoa.biosso.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import com.fijalkoa.biosso.util.JwtUtil;
import java.io.IOException;

@Component
public class JwtAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationSuccessHandler(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException {
//        String email = authentication.getName();
//        String token = jwtUtil.generateToken(email);
//
//        String redirectUri = request.getParameter("redirect_uri");
//        if (redirectUri == null || redirectUri.isBlank()) {
//            redirectUri = "http://planner-app:5000/biosso-callback";
//        }
//        response.sendRedirect(redirectUri + "?token=" + token + "&email=" + email);
    }

}
