package com.ratemyworkplace.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Container for outbound response payloads. */
public final class Responses {

    private Responses() { }

    public record UserDto(
            Long id, String displayName, String username, String email, String phoneNumber,
            String role, Set<String> moderatorPermissions, boolean emailVerified, boolean phoneVerified,
            boolean fullyVerified, boolean enabled, String flaggedReason, Instant createdAt, Instant lastLoginAt) {
    }

    public record CategoryDto(Long id, String name, String slug) {
    }

    public record LocationDto(
            Long id, Long companyId, String label, String addressLine, String city, String state,
            String zipCode, String country, double averageRating, long ratingCount) {
    }

    public record CompanySummaryDto(
            Long id, String name, String website, String logoUrl, Set<String> categories,
            double averageRating, long ratingCount, int locationCount, String status) {
    }

    public record CompanyDetailDto(
            Long id, String name, String description, String website, String logoUrl,
            Set<CategoryDto> categories, List<LocationDto> locations,
            double averageRating, long ratingCount, String status, Instant createdAt) {
    }

    public record FeedbackDto(
            Long id, Long companyId, Long locationId, String locationLabel, String authorDisplayName,
            int rating, String title, String body, String status, Instant createdAt) {
    }

    public record ProofDto(
            Long id, Long companyId, String companyName, Long locationId, String locationLabel,
            String originalFileName, String contentType, String note, String status,
            String reviewNote, Instant createdAt, Instant reviewedAt) {
    }

    public record SiteFeedbackDto(
            Long id, String authorUsername, String contactEmail, String category,
            String message, boolean resolved, Instant createdAt) {
    }

    public record SiteUpdateDto(
            Long id, String title, String body, String tag, String authorDisplayName,
            boolean published, Instant createdAt) {
    }

    public record StatsDto(
            long totalUsers, long verifiedUsers, long admins, long moderators, long newUsersLast30Days,
            long totalCompanies, long approvedCompanies, long pendingCompanies,
            long totalLocations, long totalFeedback, long pendingProofs, long pendingFeedback,
            long openSiteFeedback, List<DailyPoint> traffic) {
    }

    public record DailyPoint(String day, long pageViews, long logins, long signups) {
    }

    public record PublicStatsDto(long totalCompanies, long totalFeedback, long verifiedUsers) {
    }

    public record SimpleMessage(int status, String message, Map<String, Object> data) {
        public static SimpleMessage ok(String message) {
            return new SimpleMessage(200, message, Map.of());
        }

        public static SimpleMessage ok(String message, Map<String, Object> data) {
            return new SimpleMessage(200, message, data);
        }
    }
}
