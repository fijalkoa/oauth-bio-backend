package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.repository.UserRepository;
import com.fijalkoa.biosso.service.AuthorizationCodeService;
import com.fijalkoa.biosso.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

@Controller
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class AuthorizationController {

    private final AuthorizationCodeService codeService;
    private final ClientService clientService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/authorize")
    public String authorize(
            @RequestParam String response_type,
            @RequestParam String client_id,
            @RequestParam String redirect_uri,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String nonce,
            Model model) {


        if (!clientService.validateClient(client_id, redirect_uri)) {
            return "error";
        }
        model.addAttribute("action", "/oauth2/authorize");
        model.addAttribute("isOidc", true);
        model.addAttribute("redirect_uri", redirect_uri);
        model.addAttribute("client_id", client_id);
        model.addAttribute("state", state);
        model.addAttribute("nonce", nonce);
        return "login";
    }

    @PostMapping("/authorize")
    public RedirectView handleLogin(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String redirect_uri,
            @RequestParam String client_id,
            @RequestParam(required = false, name = "code_challenge") String codeChallenge,
            @RequestParam(required = false, name = "code_challenge_method") String codeChallengeMethod,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String nonce
    ){

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return new RedirectView("/oauth2/authorize?error=invalid_login");
        }

        if (codeChallenge != null && codeChallengeMethod == null) {
            codeChallengeMethod = "S256";
        }

        String code = codeService.generateCode(
                userOpt.get(),
                client_id,
                redirect_uri,
                codeChallenge,
                codeChallengeMethod,
                nonce
        );

        String redirect = redirect_uri + "?code=" + code;
        if (state != null) redirect += "&state=" + state;

        return new RedirectView(redirect);
    }
}

