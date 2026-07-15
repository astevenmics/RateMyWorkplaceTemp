package com.myworktea.web;

import com.myworktea.domain.Location;
import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Responses;
import com.myworktea.service.CompanyService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final CompanyService companyService;

    public LocationController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Public browse/search, one card per location rather than per company — a company
     * with 20 locations surfaces as 20 distinct cards, each with that location's own
     * address and rating.
     */
    @GetMapping
    public Page<Responses.LocationCardDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "top") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Page<Location> locations = companyService.searchLocations(q, categoryId, sort, page, size);
        return locations.map(DtoMapper::locationCard);
    }
}
