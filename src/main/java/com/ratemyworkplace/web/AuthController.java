package com.ratemyworkplace.web;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.domain.VerificationToken;
import com.ratemyworkplace.dto.DtoMapper;
import com.ratemyworkplace.dto.Requests;
import com.ratemyworkplace.dto.Responses;
import com.ratemyworkplace.service.CurrentUserService;
import com.ratemyworkplace.service.UserService;
import com.ratemyworkplace.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final VerificationService verificationService;
    private final CurrentUserService currentUserService;

    public AuthController(UserService userService, VerificationService verificationService,
                          CurrentUserService currentUserService) {
        this.userService = userService;
        this.verificationService = verificationService;
        this.currentUserService = currentUserService;
    }

    /** Lets the JS frontend prime the {@code XSRF-TOKEN} cookie before its first write. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<Responses.UserDto> register(@Valid @RequestBody Requests.RegisterRequest request) {
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DtoMapper.user(user));
    }

    /** Returns the current user, or {@code authenticated:false} for anonymous visitors. */
    @GetMapping("/me")
    public ResponseEntity<?> me() {
        return currentUserService.current()
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(DtoMapper.user(u)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("authenticated", false)));
    }

    @PostMapping("/verify")
    public Responses.UserDto verify(@Valid @RequestBody Requests.VerifyRequest request) {
        User user = currentUserService.require();
        VerificationToken.Channel channel = VerificationToken.Channel.valueOf(request.channel());
        verificationService.verify(user, channel, request.code());
        return DtoMapper.user(currentUserService.require());
    }

    @PostMapping("/verify/resend")
    public Responses.SimpleMessage resend(@Valid @RequestBody Requests.ResendVerificationRequest request) {
        User user = currentUserService.require();
        VerificationToken.Channel channel = VerificationToken.Channel.valueOf(request.channel());
        verificationService.issue(user, channel);
        return Responses.SimpleMessage.ok("A new code has been sent");
    }
}