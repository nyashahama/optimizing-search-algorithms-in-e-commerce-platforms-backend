package com.nyasha.store.security;

import com.nyasha.store.entities.Role;
import com.nyasha.store.entities.User;
import com.nyasha.store.repositories.RoleRepository;
import com.nyasha.store.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    @Test
    void loadUserByUsernameAddsRolesAndDefaultUserRole() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository, roleRepository);

        User user = new User();
        user.setEmail("admin@example.com");
        user.setHashedPassword("{bcrypt}hash");
        user.setRoles(Set.of(role("ADMIN")));

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        UserDetails loaded = service.loadUserByUsername("admin@example.com");

        assertThat(loaded.getUsername()).isEqualTo("admin@example.com");
        assertThat(loaded.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(loaded.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void loadUserByUsernameFallsBackToDefaultUserRoleWhenNoRolesAssigned() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository, roleRepository);

        User user = new User();
        user.setEmail("member@example.com");
        user.setHashedPassword("{bcrypt}hash");

        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(user));

        UserDetails loaded = service.loadUserByUsername("member@example.com");

        assertThat(loaded.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void roleByNameRejectsUnknownRoles() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository, roleRepository);

        when(roleRepository.findByNameIgnoreCase("GUEST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.roleByName("guest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Required security role missing");
    }

    @Test
    void loadUserByUsernameRejectsMissingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(userRepository, roleRepository);

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
