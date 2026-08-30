package com.kologeski.interviewassistant.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationService authentication;
    private final IdentityService identity;
    public AuthController(AuthenticationService authentication, IdentityService identity) {
        this.authentication = authentication; this.identity = identity;
    }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    AuthenticationService.AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servlet) {
        return authentication.register(request, metadata(request.deviceName(), servlet));
    }
    @PostMapping("/login")
    AuthenticationService.AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servlet) {
        return authentication.login(request, metadata(request.deviceName(), servlet));
    }
    @PostMapping("/refresh")
    AuthenticationService.AuthResponse refresh(@Valid @RequestBody TokenRequest request, HttpServletRequest servlet) {
        return authentication.refresh(request.refreshToken(), metadata(null, servlet));
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody TokenRequest request) { authentication.logout(request.refreshToken()); }

    @PostMapping("/email-verification/request") @ResponseStatus(HttpStatus.ACCEPTED)
    IdentityDelivery.DeliveryReceipt requestVerification() { return identity.requestVerification(); }

    @PostMapping("/email-verification/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    void verifyEmail(@Valid @RequestBody IdentityTokenRequest request) { identity.verifyEmail(request.token()); }

    @PostMapping("/password/forgot") @ResponseStatus(HttpStatus.ACCEPTED)
    IdentityDelivery.DeliveryReceipt forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return identity.requestPasswordReset(request.email());
    }

    @PostMapping("/password/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        identity.resetPassword(request.token(), request.password());
    }

    private RefreshTokenService.Metadata metadata(String device, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return new RefreshTokenService.Metadata(device, request.getHeader("User-Agent"), ip);
    }
    public record TokenRequest(@NotBlank @Size(max = 4096) String refreshToken) { }
    public record IdentityTokenRequest(@NotBlank @Size(max = 4096) String token) { }
    public record ForgotPasswordRequest(@NotBlank @jakarta.validation.constraints.Email @Size(max = 180) String email) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 4096) String token,
                                       @NotBlank @Size(min = 8, max = 72) String password) { }
}
