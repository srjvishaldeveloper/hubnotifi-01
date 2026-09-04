package com.whatsmine.controller.api;

import com.whatsmine.model.PersonalAccessToken;
import com.whatsmine.model.User;
import com.whatsmine.repository.PersonalAccessTokenRepository;
import com.whatsmine.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tokens")
public class TokenApiController {

    private final PersonalAccessTokenRepository tokenRepository;

    public TokenApiController(PersonalAccessTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @GetMapping
    public ResponseEntity<List<PersonalAccessToken>> index(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        return ResponseEntity.ok(tokenRepository.findByTokenableTypeAndTokenableId(user.getClass().getName(), user.getId()));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> store(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                     @RequestBody Map<String, Object> payload) {
        User user = userDetails.getUser();
        String name = (String) payload.get("name");
        String plainTextToken = UUID.randomUUID().toString().replace("-", "");

        PersonalAccessToken token = new PersonalAccessToken();
        token.setTokenableType(user.getClass().getName());
        token.setTokenableId(user.getId());
        token.setName(name != null ? name : "API Token");
        token.setToken(plainTextToken);
        tokenRepository.save(token);

        return ResponseEntity.ok(Map.of("token", token, "plainTextToken", plainTextToken));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> destroy(@PathVariable Long id) {
        tokenRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
