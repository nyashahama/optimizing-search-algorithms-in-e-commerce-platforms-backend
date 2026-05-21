package com.nyasha.store.utils;

import com.nyasha.store.entities.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserIndexTest {

    private final UserIndex index = new UserIndex();

    @Test
    void searchMatchesNameAndEmailPrefixes() {
        User user = user(1L, "Nyasha Hama", "nyasha@example.com");
        index.insert(user);

        assertThat(index.search("nya")).extracting(User::getUserId).containsExactly(1L);
        assertThat(index.search("nyasha@")).extracting(User::getUserId).containsExactly(1L);
    }

    @Test
    void blankSearchReturnsEmptyResults() {
        index.insert(user(1L, "Nyasha Hama", "nyasha@example.com"));

        assertThat(index.search("")).isEmpty();
        assertThat(index.search(null)).isEmpty();
        assertThat(index.search(" ")).isEmpty();
    }

    @Test
    void updateRemovesOldKeysAndAddsNewKeys() {
        User user = user(1L, "Nyasha Hama", "nyasha@example.com");
        index.insert(user);

        user.setName("Backend Engineer");
        user.setEmail("backend@example.com");
        index.update("Nyasha Hama", "nyasha@example.com", user);

        assertThat(index.search("nya")).isEmpty();
        assertThat(index.search("backend")).extracting(User::getUserId).containsExactly(1L);
    }

    @Test
    void removeDeletesIndexedUser() {
        User user = user(1L, "Nyasha Hama", "nyasha@example.com");
        index.insert(user);

        index.remove(user);

        assertThat(index.search("nya")).isEmpty();
        assertThat(index.search("nyasha@")).isEmpty();
    }

    private User user(Long id, String name, String email) {
        User user = new User();
        user.setUserId(id);
        user.setName(name);
        user.setEmail(email);
        user.setHashedPassword("hash");
        return user;
    }
}
