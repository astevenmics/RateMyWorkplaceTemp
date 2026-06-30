package com.ratemyworkplace.config;

import com.ratemyworkplace.domain.*;
import com.ratemyworkplace.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Bootstraps the platform on first run: ensures an admin account exists, seeds a
 * starter set of categories, and (when the DB is empty) inserts a little demo data
 * so the home page and listings are not blank.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final FeedbackRepository feedbackRepository;
    private final SiteUpdateRepository siteUpdateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties adminProps;

    public DataInitializer(UserRepository userRepository, CategoryRepository categoryRepository,
                           CompanyRepository companyRepository, FeedbackRepository feedbackRepository,
                           SiteUpdateRepository siteUpdateRepository, PasswordEncoder passwordEncoder,
                           AdminBootstrapProperties adminProps) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.companyRepository = companyRepository;
        this.feedbackRepository = feedbackRepository;
        this.siteUpdateRepository = siteUpdateRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProps = adminProps;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedCategories();
        if (companyRepository.count() == 0) {
            seedDemoData();
        }
        if (siteUpdateRepository.count() == 0) {
            seedFirstUpdate();
        }
    }

    private void seedAdmin() {
        if (userRepository.existsByUsernameIgnoreCase(adminProps.getUsername())) {
            return;
        }
        User admin = new User();
        admin.setFirstName("Site");
        admin.setLastName("Administrator");
        admin.setDisplayName("Administrator");
        admin.setUsername(adminProps.getUsername());
        admin.setEmail(adminProps.getEmail());
        admin.setPhoneNumber("+10000000000");
        admin.setPasswordHash(passwordEncoder.encode(adminProps.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        admin.setPhoneVerified(true);
        userRepository.save(admin);
        log.info("Created default admin account '{}'. CHANGE THE PASSWORD in production!", adminProps.getUsername());
    }

    private void seedCategories() {
        List<String> defaults = List.of("IT", "Technology", "Marketing", "Finance", "Healthcare",
                "Retail", "Hospitality", "Education", "Manufacturing", "Logistics");
        for (String name : defaults) {
            if (!categoryRepository.existsByNameIgnoreCase(name)) {
                categoryRepository.save(new Category(name, com.ratemyworkplace.service.CategoryService.slugify(name)));
            }
        }
    }

    private void seedDemoData() {
        Category tech = categoryRepository.findByNameIgnoreCase("Technology").orElse(null);
        Category retail = categoryRepository.findByNameIgnoreCase("Retail").orElse(null);
        Category hospitality = categoryRepository.findByNameIgnoreCase("Hospitality").orElse(null);

        User demoAuthor = userRepository.findByUsernameIgnoreCase("demo_reviewer").orElseGet(() -> {
            User u = new User();
            u.setFirstName("Jordan");
            u.setLastName("Avery");
            u.setDisplayName("Jordan A.");
            u.setUsername("demo_reviewer");
            u.setEmail("demo.reviewer@ratemyworkplace.local");
            u.setPhoneNumber("+10000000001");
            u.setPasswordHash(passwordEncoder.encode("Demo@12345"));
            u.setRole(Role.USER);
            u.setEmailVerified(true);
            u.setPhoneVerified(true);
            return userRepository.save(u);
        });

        Company starbucks = company("Starbucks",
                "Global coffeehouse chain known for its café culture and seasonal drinks.",
                "https://www.starbucks.com", Set.of(retail, hospitality));
        addLocation(starbucks, "Downtown", "123 Pike St", "Seattle", "WA", "98101");
        addLocation(starbucks, "Midtown", "55 W 42nd St", "New York", "NY", "10036");

        Company acme = company("Acme Software",
                "A product-led software company building developer tools and cloud services.",
                "https://acme.example.com", Set.of(tech));
        addLocation(acme, "HQ", "500 Tech Plaza", "Austin", "TX", "78701");

        Company globex = company("Globex Marketing",
                "Full-service marketing agency specialising in brand and growth campaigns.",
                "https://globex.example.com", Set.of(retail));
        addLocation(globex, "Studio", "9 Market Ave", "Chicago", "IL", "60601");

        companyRepository.save(starbucks);
        companyRepository.save(acme);
        companyRepository.save(globex);

        feedback(starbucks, demoAuthor, 5, "Great team culture",
                "Supportive managers and flexible scheduling. Genuinely enjoyed working here.");
        feedback(starbucks, demoAuthor, 4, "Solid place to start",
                "Fast-paced but fair. Good training and benefits for part-timers.");
        feedback(acme, demoAuthor, 5, "Engineer-friendly",
                "Strong engineering culture, modern stack, and real work-life balance.");
        feedback(globex, demoAuthor, 3, "Mixed bag",
                "Creative projects but deadlines can get intense around campaign launches.");

        recompute(starbucks);
        recompute(acme);
        recompute(globex);
        log.info("Seeded demo workplaces and feedback.");
    }

    private Company company(String name, String description, String website, Set<Category> categories) {
        Company c = new Company();
        c.setName(name);
        c.setDescription(description);
        c.setWebsite(website);
        c.setStatus(ApprovalStatus.APPROVED);
        categories.stream().filter(java.util.Objects::nonNull).forEach(c.getCategories()::add);
        return c;
    }

    private void addLocation(Company company, String label, String address, String city, String state, String zip) {
        Location l = new Location();
        l.setCompany(company);
        l.setLabel(label);
        l.setAddressLine(address);
        l.setCity(city);
        l.setState(state);
        l.setZipCode(zip);
        l.setCountry("USA");
        company.getLocations().add(l);
    }

    private void feedback(Company company, User author, int rating, String title, String body) {
        Location location = company.getLocations().get(0);
        Feedback f = new Feedback();
        f.setCompany(company);
        f.setLocation(location);
        f.setAuthor(author);
        f.setRating(rating);
        f.setTitle(title);
        f.setBody(body);
        f.setStatus(ApprovalStatus.APPROVED);
        feedbackRepository.save(f);
    }

    private void recompute(Company company) {
        for (Location location : company.getLocations()) {
            location.setAverageRating(feedbackRepository.averageForLocation(location.getId()));
            location.setRatingCount(feedbackRepository.countForLocation(location.getId()));
        }
        company.setAverageRating(feedbackRepository.averageForCompany(company.getId()));
        company.setRatingCount(feedbackRepository.countForCompany(company.getId()));
        companyRepository.save(company);
    }

    private void seedFirstUpdate() {
        SiteUpdate update = new SiteUpdate();
        update.setTitle("Welcome to RateMyWorkplace 🎉");
        update.setTag("launch");
        update.setBody("RateMyWorkplace is live! Search workplaces, read verified employee feedback, and "
                + "share your own experience after verifying your employment. New features are on the way — "
                + "watch this space.");
        update.setPublished(true);
        siteUpdateRepository.save(update);
    }
}
