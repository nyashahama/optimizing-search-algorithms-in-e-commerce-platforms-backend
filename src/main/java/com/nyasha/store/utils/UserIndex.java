package com.nyasha.store.utils;

import com.nyasha.store.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Component
public class UserIndex {

    private static final Logger logger = LoggerFactory.getLogger(UserIndex.class);

    // Fast lookup indexes using ConcurrentHashMap.
    // Instead of CopyOnWriteArrayList, we use synchronized lists for better write performance.
    private final ConcurrentMap<String, List<User>> fastIndexByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<User>> fastIndexByEmail = new ConcurrentHashMap<>();

    // Sorted indexes using ConcurrentSkipListMap for efficient prefix/range searches.
    private final ConcurrentSkipListMap<String, List<User>> sortedIndexByName = new ConcurrentSkipListMap<>();
    private final ConcurrentSkipListMap<String, List<User>> sortedIndexByEmail = new ConcurrentSkipListMap<>();

    /**
     * Helper method to get or create a synchronized list from the given map.
     */

    // A common lock to ensure atomic index updates. Adjust granularity as needed.
    private final Object indexLock = new Object();

    private List<User> getOrCreateList(ConcurrentMap<String, List<User>> map, String key) {
        return map.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Inserts a user into both the fast (ConcurrentHashMap) and sorted (ConcurrentSkipListMap) indexes.
     */
    public void insert(User user) {
        if (user == null) {
            return;
        }

        if (user.getName() == null) {
            return;
        }
        if (user.getEmail() == null) {
            return;
        }

        String nameKey = user.getName().toLowerCase();
        String emailKey = user.getEmail().toLowerCase();
        try {
            synchronized (indexLock) {
                getOrCreateList(fastIndexByName, nameKey).add(user);
                getOrCreateList(fastIndexByEmail, emailKey).add(user);
                getOrCreateList(sortedIndexByName, nameKey).add(user);
                getOrCreateList(sortedIndexByEmail, emailKey).add(user);
            }
            logger.debug("Inserted user {} into indexes", user.getUserId());
        }catch (Exception e){
            logger.error("Error inserting user {} into indexes: {}", user.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Removes a user from both the fast and sorted indexes.
     */
    public void remove(User user) {
        if (user == null) {
            return;
        }

        if (user.getName() == null || user.getEmail() == null) {
            return;
        }

        String nameKey = user.getName().toLowerCase();
        String emailKey = user.getEmail().toLowerCase();
        try {
            synchronized (indexLock) {
                removeFromIndex(fastIndexByName, nameKey, user);
                removeFromIndex(fastIndexByEmail, emailKey, user);
                removeFromIndex(sortedIndexByName, nameKey, user);
                removeFromIndex(sortedIndexByEmail, emailKey, user);
            }
            logger.debug("Removed user {} from indexes", user.getUserId());
        }catch  (Exception e){
            logger.error("Error removing user {} from indexes: {}",user.getUserId(),e.getMessage(),e);
        }
    }

    /**
     * Updates a user in the index.
     * Since the user's name and/or email might change, we remove the user using the old keys and then insert the updated user.
     *
     * @param oldName     The user's name before update.
     * @param oldEmail    The user's email before update.
     * @param updatedUser The user object after update.
     */
    public void update(String oldName, String oldEmail, User updatedUser) {
        if (updatedUser == null) {
            return;
        }

        if (oldName == null || oldEmail == null) {
            insert(updatedUser);
            return;
        }

        String oldNameKey = oldName.toLowerCase();
        String oldEmailKey = oldEmail.toLowerCase();
        try {
            // Remove using the old keys.
            synchronized (indexLock) {
                removeFromIndex(fastIndexByName, oldNameKey, updatedUser);
                removeFromIndex(fastIndexByEmail, oldEmailKey, updatedUser);
                removeFromIndex(sortedIndexByName, oldNameKey, updatedUser);
                removeFromIndex(sortedIndexByEmail, oldEmailKey, updatedUser);

                // Insert the updated user with the new keys.
                insert(updatedUser);
            }
            logger.debug("Updated user {} in indexes", updatedUser.getUserId());
        }catch (Exception e){
            logger.error("Error updating user {} in indexes: {}", updatedUser.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Searches for users whose name or email starts with the given prefix.
     * Uses the sorted indexes (ConcurrentSkipListMap) for efficient retrieval.
     */
    public List<User> search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return Collections.emptyList();
        }

        String prefix = searchTerm.toLowerCase();
        Set<User> results = new HashSet<>();
        try {
            synchronized (indexLock) {
                results.addAll(searchByPrefix(sortedIndexByName, prefix));
                results.addAll(searchByPrefix(sortedIndexByEmail, prefix));
            }
            logger.debug("Search for prefix '{}' returned {} results", prefix, results.size());
        }catch (Exception e){
            logger.error("Search error for prefix '{}': {}", prefix, e.getMessage(), e);
        }
        return new ArrayList<>(results);
    }

    /**
     * Helper method to remove a user from an index.
     */
    private void removeFromIndex(ConcurrentMap<String, List<User>> index, String key, User user) {
        List<User> list = index.get(key);
        if (list != null) {
            synchronized (list) {
                list.removeIf(candidate -> sameUser(candidate, user));
                if (list.isEmpty()) {
                    index.remove(key, list);
                }
            }
        }
    }

    /**
     * Helper method that searches a ConcurrentSkipListMap index for keys starting with the given prefix.
     */
    private List<User> searchByPrefix(ConcurrentSkipListMap<String, List<User>> map, String prefix) {
        List<User> matches = new ArrayList<>();
        NavigableMap<String, List<User>> tailMap = map.tailMap(prefix, true);
        for (Map.Entry<String, List<User>> entry : tailMap.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                break;
            }
            synchronized (entry.getValue()) {
                matches.addAll(entry.getValue());
            }
        }
        return matches;
    }

    private boolean sameUser(User a, User b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getUserId(), b.getUserId()) || a == b;
    }
}
