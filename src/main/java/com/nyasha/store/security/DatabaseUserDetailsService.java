package com.nyasha.store.security;

import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public DatabaseUserDetailsService(
            UserRepository userRepository,
            RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getHashedPassword())
                .authorities(resolveAuthorities(user))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    private List<GrantedAuthority> resolveAuthorities(User user) {
        if (user == null) {
            return emptyList();
        }

        Stream<String> userRoles = Stream.concat(
                user.getRoles() == null || user.getRoles().isEmpty()
                        ? Stream.empty()
                        : user.getRoles().stream().map(Role::getName),
                Stream.of("USER")
        );

        return userRoles
                .filter(StringUtils::hasText)
                .map(role -> role.strip().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .distinct()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    public Role roleByName(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            throw new IllegalArgumentException("Role name is required");
        }
        return roleRepository.findByNameIgnoreCase(roleName)
                .orElseThrow(() -> new IllegalStateException("Required security role missing: " + roleName));
    }
}
