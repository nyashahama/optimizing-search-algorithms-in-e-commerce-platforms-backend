package com.nyasha.store.security;

import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;

@Component
public class SecurityBootstrapService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SecurityBootstrapService.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseUserDetailsService databaseUserDetailsService;
    private final boolean bootstrapEnabled;
    private final String bootstrapAdminEmail;
    private final String bootstrapAdminPassword;
    private final String bootstrapAdminName;

    public SecurityBootstrapService(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DatabaseUserDetailsService databaseUserDetailsService,
            @Value("${security.bootstrap.admin.enabled:true}") boolean bootstrapEnabled,
            @Value("${security.bootstrap.admin.email:}") String bootstrapAdminEmail,
            @Value("${security.bootstrap.admin.password:}") String bootstrapAdminPassword,
            @Value("${security.bootstrap.admin.name:Admin User}") String bootstrapAdminName
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseUserDetailsService = databaseUserDetailsService;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
        this.bootstrapAdminName = bootstrapAdminName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureBaseRoles();
        if (!bootstrapEnabled) {
            logger.info("Security bootstrap is disabled");
            return;
        }

        ensureBootstrapAdmin();
    }

    @Transactional
    protected void ensureBaseRoles() {
        ensureRole("USER");
        ensureRole("ADMIN");
    }

    @Transactional
    protected void ensureBootstrapAdmin() {
        if (!StringUtils.hasText(bootstrapAdminEmail) || !StringUtils.hasText(bootstrapAdminPassword)) {
            logger.warn(
                    "Bootstrap admin credentials are not configured. Set SECURITY_BOOTSTRAP_ADMIN_EMAIL and "
                            + "SECURITY_BOOTSTRAP_ADMIN_PASSWORD to create an initial admin user."
            );
            return;
        }

        Role adminRole = databaseUserDetailsService.roleByName("ADMIN");
        userRepository.findByEmail(bootstrapAdminEmail).ifPresentOrElse(
                user -> {
                    if (user.getRoles() == null || user.getRoles().stream().noneMatch(role -> isAdminRole(role))) {
                        user.getRoles().add(adminRole);
                        userRepository.save(user);
                        logger.info("Existing bootstrap user {} was promoted to admin role", bootstrapAdminEmail);
                    }
                },
                () -> {
                    User admin = new User();
                    admin.setName(bootstrapAdminName);
                    admin.setEmail(bootstrapAdminEmail);
                    admin.setHashedPassword(passwordEncoder.encode(bootstrapAdminPassword));
                    admin.setCreatedAt(LocalDateTime.now());
                    admin.setRoles(new HashSet<>());
                    admin.getRoles().add(adminRole);
                    userRepository.save(admin);
                    logger.info("Created bootstrap admin user {}", bootstrapAdminEmail);
                }
        );
    }

    private void ensureRole(String roleName) {
        roleRepository.findByNameIgnoreCase(roleName).orElseGet(() -> roleRepository.save(new Role(roleName.toUpperCase(Locale.ROOT))));
    }

    private boolean isAdminRole(Role role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.getName());
    }
}
