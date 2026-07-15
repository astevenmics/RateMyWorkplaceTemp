package com.myworktea.service;

import com.myworktea.domain.*;
import com.myworktea.dto.Requests;
import com.myworktea.repository.CompanyRepository;
import com.myworktea.repository.FeedbackRepository;
import com.myworktea.repository.LocationRepository;
import com.myworktea.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final FeedbackRepository feedbackRepository;
    private final CategoryService categoryService;

    public CompanyService(CompanyRepository companyRepository, LocationRepository locationRepository,
                          FeedbackRepository feedbackRepository, CategoryService categoryService) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.feedbackRepository = feedbackRepository;
        this.categoryService = categoryService;
    }

    /** Public, approved-only search with optional keyword + category and a safe sort whitelist. */
    public Page<Company> search(String query, Long categoryId, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveSort(sort));
        String q = StringUtils.hasText(query) ? query.trim() : null;
        return companyRepository.search(q, categoryId, ApprovalStatus.APPROVED, pageable);
    }

    /** Same search, but one row per location instead of per company (for the browse-page cards). */
    public Page<Location> searchLocations(String query, Long categoryId, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), resolveLocationSort(sort));
        String q = StringUtils.hasText(query) ? query.trim() : null;
        return locationRepository.searchCards(q, categoryId, ApprovalStatus.APPROVED, pageable);
    }

    public List<Company> topRated(int limit) {
        return companyRepository.findTopRated(ApprovalStatus.APPROVED, PageRequest.of(0, Math.max(1, limit)));
    }

    public Company getApproved(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Workplace not found"));
        if (company.getStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.notFound("Workplace not found");
        }
        return company;
    }

    public Company getAny(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Workplace not found"));
    }

    @Transactional
    public Company submit(User submitter, Requests.CompanySubmissionRequest req) {
        Company company = new Company();
        company.setName(req.name().trim());
        company.setDescription(req.description());
        company.setWebsite(req.website());
        company.setSubmittedBy(submitter);
        company.setStatus(ApprovalStatus.PENDING);

        if (req.categories() != null) {
            Set<Category> categories = new HashSet<>();
            for (String name : req.categories()) {
                if (StringUtils.hasText(name)) {
                    categories.add(categoryService.findOrCreate(name));
                }
            }
            company.setCategories(categories);
        }

        for (Requests.LocationRequest lr : req.locations()) {
            Location location = new Location();
            location.setCompany(company);
            location.setLabel(lr.label());
            location.setAddressLine(lr.addressLine());
            location.setCity(lr.city());
            location.setState(lr.state());
            location.setZipCode(lr.zipCode());
            location.setCountry(StringUtils.hasText(lr.country()) ? lr.country() : "USA");
            location.setDepartments(parseDepartments(lr.departments()));
            company.getLocations().add(location);
        }
        return companyRepository.save(company);
    }

    @Transactional
    public Company adminCreate(User admin, Requests.CompanySubmissionRequest req) {
        Company company = new Company();
        company.setName(req.name().trim());
        company.setDescription(req.description());
        company.setWebsite(req.website());
        company.setSubmittedBy(admin);
        company.setStatus(ApprovalStatus.APPROVED);

        if (req.categories() != null) {
            Set<Category> categories = new HashSet<>();
            for (String name : req.categories()) {
                if (StringUtils.hasText(name)) {
                    categories.add(categoryService.findOrCreate(name));
                }
            }
            company.setCategories(categories);
        }

        for (Requests.LocationRequest lr : req.locations()) {
            Location location = new Location();
            location.setCompany(company);
            location.setLabel(lr.label());
            location.setAddressLine(lr.addressLine());
            location.setCity(lr.city());
            location.setState(lr.state());
            location.setZipCode(lr.zipCode());
            location.setCountry(StringUtils.hasText(lr.country()) ? lr.country() : "USA");
            location.setDepartments(parseDepartments(lr.departments()));
            company.getLocations().add(location);
        }
        return companyRepository.save(company);
    }

    @Transactional
    public Company adminUpdate(Long id, Requests.CompanySubmissionRequest req) {
        Company company = getAny(id);
        company.setName(req.name().trim());
        company.setDescription(req.description());
        company.setWebsite(req.website());

        Set<Category> categories = new HashSet<>();
        if (req.categories() != null) {
            for (String name : req.categories()) {
                if (StringUtils.hasText(name)) {
                    categories.add(categoryService.findOrCreate(name));
                }
            }
        }
        company.setCategories(categories);

        java.util.Map<Long, Location> existing = company.getLocations().stream()
                .collect(java.util.stream.Collectors.toMap(Location::getId, l -> l));
        for (Requests.LocationRequest lr : req.locations()) {
            Location location = lr.id() != null ? existing.get(lr.id()) : null;
            if (location == null) {
                location = new Location();
                location.setCompany(company);
                company.getLocations().add(location);
            }
            location.setLabel(lr.label());
            location.setAddressLine(lr.addressLine());
            location.setCity(lr.city());
            location.setState(lr.state());
            location.setZipCode(lr.zipCode());
            location.setCountry(StringUtils.hasText(lr.country()) ? lr.country() : "USA");
            location.setDepartments(parseDepartments(lr.departments()));
        }
        return companyRepository.save(company);
    }

    private static final int MAX_DEPARTMENTS_PER_LOCATION = 20;

    private Set<String> parseDepartments(Set<String> raw) {
        return Department.normalizeSet(raw, MAX_DEPARTMENTS_PER_LOCATION);
    }

    /** Recomputes denormalized rating aggregates for a location and its parent company. */
    @Transactional
    public void recomputeAggregates(Location location) {
        FeedbackRepository.RatingStats locationStats = feedbackRepository.locationStats(location.getId());
        location.setAverageRating(locationStats.getAverage());
        location.setRatingCount(locationStats.getCount());
        locationRepository.save(location);

        Company company = location.getCompany();
        FeedbackRepository.RatingStats companyStats = feedbackRepository.companyStats(company.getId());
        company.setAverageRating(companyStats.getAverage());
        company.setRatingCount(companyStats.getCount());
        companyRepository.save(company);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 30;
        }
        return Math.min(size, 100);
    }

    private Sort resolveSort(String sort) {
        if (sort == null) {
            sort = "top";
        }
        return switch (sort.toLowerCase()) {
            case "az", "name", "alpha" -> Sort.by(Sort.Direction.ASC, "name");
            case "za" -> Sort.by(Sort.Direction.DESC, "name");
            case "newest", "new" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "rated", "reviews", "popular" -> Sort.by(Sort.Direction.DESC, "ratingCount");
            default -> Sort.by(Sort.Direction.DESC, "averageRating")
                    .and(Sort.by(Sort.Direction.DESC, "ratingCount"));
        };
    }

    /** Same sort keys as resolveSort(), but against Location's own fields (and the parent company's name). */
    private Sort resolveLocationSort(String sort) {
        if (sort == null) {
            sort = "top";
        }
        return switch (sort.toLowerCase()) {
            case "az", "name", "alpha" -> Sort.by(Sort.Direction.ASC, "company.name");
            case "za" -> Sort.by(Sort.Direction.DESC, "company.name");
            case "newest", "new" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "rated", "reviews", "popular" -> Sort.by(Sort.Direction.DESC, "ratingCount");
            default -> Sort.by(Sort.Direction.DESC, "averageRating")
                    .and(Sort.by(Sort.Direction.DESC, "ratingCount"));
        };
    }
}