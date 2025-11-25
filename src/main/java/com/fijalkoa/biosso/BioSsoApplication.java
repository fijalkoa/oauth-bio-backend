package com.fijalkoa.biosso;

import com.fijalkoa.biosso.model.Client;
import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.repository.ClientRepository;
import com.fijalkoa.biosso.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@SpringBootApplication
public class BioSsoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioSsoApplication.class, args);
    }

    @Bean
    CommandLineRunner initClients(UserRepository userRepo, ClientRepository clientRepo, PasswordEncoder encoder) {
        return args -> {
            if (userRepo.findByEmail("ania@ania.com").isEmpty()) {
                User u = new User();
                u.setEmail("ania@ania.com");
                u.setPassword(encoder.encode("password123"));
                userRepo.save(u);
                System.out.println("✅ Test user created: ania@ania.com / password123");
            }

            if (clientRepo.findByClientId("89afnhi34oisdio203s").isEmpty()) {
                Client client = Client.builder()
                        .clientId("89afnhi34oisdio203s")
                        .clientSecret(encoder.encode("secret123")) // encode for security
                        .name("Test Client App")
                        .redirectUris(Set.of("http://localhost:5000/auth/biosso-callback"))
                        .allowedScopes(Set.of("openid", "profile", "email"))
                        .build();

                clientRepo.save(client);
            }
        };
    }

}
