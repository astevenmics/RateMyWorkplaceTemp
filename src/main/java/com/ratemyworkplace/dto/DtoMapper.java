package com.ratemyworkplace.dto;

import com.ratemyworkplace.domain.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure mapping helpers from JPA entities to API DTOs. */
public final class DtoMapper {

    private DtoMapper() { }

    public static Responses.UserDto user(User u) {
        return new Responses.UserDto(
                u.getId(), u.getDisplayName(), u.getUsername(), u.getEmail(), u.getPhoneNumber(),
                u.getRole().name(),
                u.getModeratorPermissions().stream().map(Enum::name).collect(Collectors.toSet()),
                u.isEmailVerified(), u.isPhoneVerified(), u.isFullyVerified(), u.isEnabled(),
                u.getFlaggedReason(), u.getCreatedAt(), u.getLastLoginAt());
    }

    public static Responses.CategoryDto category(Category c) {
        return new Responses.CategoryDto(c.getId(), c.getName(), c.getSlug());
    }

    public static Responses.LocationDto location(Location l) {
        return new Responses.LocationDto(
                l.getId(), l.getCompany() != null ? l.getCompany().getId() : null, l.getLabel(),
                l.getAddressLine(), l.getCity(), l.getState(), l.getZipCode(), l.getCountry(),
                round(l.getAverageRating()), l.getRatingCount());
    }

    public static Responses.CompanySummaryDto companySummary(Company c) {
        return new Responses.CompanySummaryDto(
                c.getId(), c.getName(), c.getWebsite(), c.getLogoUrl(),
                c.getCategories().stream().map(Category::getName).collect(Collectors.toCollection(java.util.TreeSet::new)),
                round(c.getAverageRating()), c.getRatingCount(), c.getLocations().size(), c.getStatus().name());
    }

    public static Responses.CompanyDetailDto companyDetail(Company c) {
        Set<Responses.CategoryDto> cats = c.getCategories().stream()
                .map(DtoMapper::category).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Responses.LocationDto> locs = c.getLocations().stream()
                .map(DtoMapper::location).collect(Collectors.toList());
        return new Responses.CompanyDetailDto(
                c.getId(), c.getName(), c.getDescription(), c.getWebsite(), c.getLogoUrl(),
                cats, locs, round(c.getAverageRating()), c.getRatingCount(), c.getStatus().name(), c.getCreatedAt());
    }

    public static Responses.FeedbackDto feedback(Feedback f) {
        return new Responses.FeedbackDto(
                f.getId(), f.getCompany().getId(), f.getLocation().getId(),
                locationLabel(f.getLocation()),
                f.getAuthor() != null ? f.getAuthor().getDisplayName() : "Anonymous",
                f.getRating(), f.getTitle(), f.getBody(), f.getStatus().name(), f.getCreatedAt());
    }

    public static Responses.ProofDto proof(EmploymentProof p) {
        User submitter = p.getUser();
        return new Responses.ProofDto(
                p.getId(), p.getCompany().getId(), p.getCompany().getName(),
                p.getLocation() != null ? p.getLocation().getId() : null,
                p.getLocation() != null ? locationLabel(p.getLocation()) : null,
                submitter != null ? submitter.getId() : null,
                submitter != null ? submitter.getDisplayName() : null,
                submitter != null ? submitter.getUsername() : null,
                p.getOriginalFileName(), p.getContentType(), p.getNote(), p.getStatus().name(),
                p.getReviewNote(), p.getCreatedAt(), p.getReviewedAt());
    }

    public static Responses.SiteFeedbackDto siteFeedback(SiteFeedback s) {
        return new Responses.SiteFeedbackDto(
                s.getId(), s.getAuthor() != null ? s.getAuthor().getUsername() : null,
                s.getContactEmail(), s.getCategory(), s.getMessage(), s.isResolved(), s.getCreatedAt());
    }

    public static Responses.SiteUpdateDto siteUpdate(SiteUpdate s) {
        return new Responses.SiteUpdateDto(
                s.getId(), s.getTitle(), s.getBody(), s.getTag(),
                s.getAuthor() != null ? s.getAuthor().getDisplayName() : "RateMyWorkplace Team",
                s.isPublished(), s.getCreatedAt());
    }

    public static String locationLabel(Location l) {
        StringBuilder sb = new StringBuilder();
        if (l.getLabel() != null && !l.getLabel().isBlank()) {
            sb.append(l.getLabel()).append(" — ");
        }
        sb.append(l.getAddressLine() == null ? "" : l.getAddressLine());
        if (l.getCity() != null) {
            sb.append(", ").append(l.getCity());
        }
        if (l.getState() != null) {
            sb.append(", ").append(l.getState());
        }
        if (l.getZipCode() != null) {
            sb.append(" ").append(l.getZipCode());
        }
        return sb.toString().trim();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
