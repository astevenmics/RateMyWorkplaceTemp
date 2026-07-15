package com.myworktea.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Set;

/** Container for inbound request payloads (kept together for readability). */
public final class Requests {

    private Requests() { }

    public record RegisterRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.]{3,40}$",
                    message = "Username must be 3-40 chars: letters, digits, underscore or dot") String username,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                    message = "Password must include at least one letter and one number") String password) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Email @Size(max = 160) String email,
            String currentPassword,
            @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                    message = "Password must include at least one letter and one number") String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                    message = "Password must include at least one letter and one number") String newPassword) {
    }

    public record VerifyRequest(@NotBlank String code) {}

    public record LocationRequest(
            Long id,
            @Size(max = 140) String label,
            @NotBlank @Size(max = 200) String addressLine,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 80) String state,
            @NotBlank @Size(max = 20) String zipCode,
            @Size(max = 80) String country,
            Set<String> departments) {
    }

    public record CompanySubmissionRequest(
            @NotBlank @Size(max = 140) String name,
            @Size(max = 4000) String description,
            @Size(max = 200) String website,
            Set<String> categories,
            @NotEmpty(message = "Add at least one location") List<@Valid LocationRequest> locations) {
    }

    public record FeedbackRequest(
            @NotNull Long locationId,
            @Min(1) @Max(5) int rating,
            @Size(max = 140) String title,
            @NotBlank @Size(max = 4000) String body,
            Set<String> departments) {
    }

    public record SiteFeedbackRequest(
            @Size(max = 60) String category,
            @Email @Size(max = 160) String contactEmail,
            @NotBlank @Size(max = 4000) String message) {
    }

    public record SiteUpdateRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 8000) String body,
            @Size(max = 40) String tag) {
    }

    public record ModeratorRequest(
            @NotBlank String username,
            @NotNull Set<String> permissions) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 60) String name) {
    }

    public record ReviewDecisionRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
            @Size(max = 255) String note) {
    }

    public record FlagUserRequest(@Size(max = 255) String reason) {
    }
}