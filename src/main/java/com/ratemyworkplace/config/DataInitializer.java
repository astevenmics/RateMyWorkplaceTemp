package com.ratemyworkplace.config;

import com.ratemyworkplace.domain.Category;
import com.ratemyworkplace.domain.Role;
import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.repository.CategoryRepository;
import com.ratemyworkplace.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties adminProps;

    public DataInitializer(UserRepository userRepository, CategoryRepository categoryRepository,
                           PasswordEncoder passwordEncoder,
                           AdminBootstrapProperties adminProps) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProps = adminProps;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedCategories();
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
        admin.setPasswordHash(passwordEncoder.encode(adminProps.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
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
}