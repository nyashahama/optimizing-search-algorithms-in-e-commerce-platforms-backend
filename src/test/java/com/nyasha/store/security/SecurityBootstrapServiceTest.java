package com.nyasha.store.security;

import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class SecurityBootstrapServiceTest {

    @Test
    void runCreatesBootstrapAdminWhenNotConfiguredSkipsCreationButKeepsRequiredRoles() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);

        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(role("USER")));
        when(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(Optional.of(role("ADMIN")));

        SecurityBootstrapService service = new SecurityBootstrapService(
                roleRepository,
                userRepository,
                passwordEncoder,
                userDetailsService,
                true,
                "",
                "",
                "Admin User"
        );

        service.run(null);

        verify(roleRepository, never()).save(any(Role.class));
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void runCreatesBootstrapAdminUserWhenMissing() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);

        Role adminRole = role("ADMIN");
        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(role("USER")));
        when(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userDetailsService.roleByName(anyString())).thenReturn(adminRole);
        when(userRepository.findByEmail("admin@localhost")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded");

        SecurityBootstrapService service = new SecurityBootstrapService(
                roleRepository,
                userRepository,
                passwordEncoder,
                userDetailsService,
                true,
                "admin@localhost",
                "secret",
                "Admin User"
        );

        service.run(null);

        verify(userRepository).save(any(User.class));
    }

    @Test
    void runPromotesExistingBootstrapUserToAdminRole() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);

        Role adminRole = role("ADMIN");
        User existingUser = new User();
        existingUser.setEmail("admin@localhost");
        existingUser.setRoles(new HashSet<>());

        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(role("USER")));
        when(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userDetailsService.roleByName(anyString())).thenReturn(adminRole);
        when(userRepository.findByEmail("admin@localhost")).thenReturn(Optional.of(existingUser));

        SecurityBootstrapService service = new SecurityBootstrapService(
                roleRepository,
                userRepository,
                passwordEncoder,
                userDetailsService,
                true,
                "admin@localhost",
                "secret",
                "Admin User"
        );

        service.run(null);

        verify(userRepository).save(existingUser);
        assertThat(existingUser.getRoles()).contains(adminRole);
    }

    @Test
    void runSkipsBootstrapWhenDisabled() throws Exception {
        RoleRepository roleRepository = mock(RoleRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        DatabaseUserDetailsService userDetailsService = mock(DatabaseUserDetailsService.class);

        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(role("USER")));
        when(roleRepository.findByNameIgnoreCase("ADMIN")).thenReturn(Optional.of(role("ADMIN")));

        SecurityBootstrapService service = new SecurityBootstrapService(
                roleRepository,
                userRepository,
                passwordEncoder,
                userDetailsService,
                false,
                "admin@localhost",
                "secret",
                "Admin User"
        );

        service.run(null);

        verify(roleRepository, never()).save(any(Role.class));
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
