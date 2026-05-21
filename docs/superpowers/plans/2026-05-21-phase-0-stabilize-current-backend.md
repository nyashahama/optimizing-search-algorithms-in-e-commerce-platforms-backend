# Phase 0 Stabilize Current Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make the current Spring Boot backend buildable, testable, documented, and safe enough to serve as the baseline for the search benchmarking lab.

**Architecture:** Phase 0 keeps the current single Spring Boot application and PostgreSQL-backed domain model. It adds test runtime configuration, targeted unit tests, safer user API responses, and project documentation before Kafka/OpenSearch work begins.

**Tech Stack:** Java 21, Spring Boot 3.4.3, Spring Web, Spring Data JPA, Spring Security, PostgreSQL runtime, H2 test database, JUnit 5, Mockito, Maven Wrapper.

---

## Scope Boundary

This plan covers only **Phase 0: Stabilize Current Backend** from `docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md`.

Do not add Kafka, OpenSearch, Docker Compose, benchmark models, or benchmark APIs in this phase. Those belong to Phase 1 and later plans.

## File Structure

- Modify `pom.xml`: add H2 test dependency if missing.
- Create `src/test/resources/application-test.properties`: isolated test profile using an in-memory H2 database.
- Modify `src/test/java/com/nyasha/store/StoreApplicationTests.java`: activate the `test` profile.
- Create `src/main/java/com/nyasha/store/dtos/UserResponse.java`: user response DTO that never exposes password hashes.
- Modify `src/main/java/com/nyasha/store/controllers/UserController.java`: use constructor injection and return `UserResponse` instead of mutating `User` entities.
- Create `src/test/java/com/nyasha/store/controllers/UserControllerTest.java`: controller-level tests for response sanitization.
- Create `src/test/java/com/nyasha/store/utils/ProductIndexTest.java`: unit tests for text search, prefix search, category search, blank queries, and update behavior.
- Create `src/test/java/com/nyasha/store/utils/UserIndexTest.java`: unit tests for prefix search, blank search, and update behavior.
- Create `src/test/java/com/nyasha/store/services/ProductServiceTest.java`: unit tests for validation and index lifecycle behavior.
- Create or modify `README.md`: document project goal, current phase, next phases, prerequisites, setup, and verification commands.
- Modify `docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md`: mark Phase 0 as current only if the implementation plan remains in progress; do not mark it complete until verification passes.

## Prerequisites

Before running verification, Java 21 must be available:

```bash
java -version
```

Expected:

```text
openjdk version "21...
```

If `java` is missing or `JAVA_HOME` is not set, install JDK 21 and export `JAVA_HOME` before continuing. On Ubuntu-style systems, the command is usually:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

---

### Task 1: Add Isolated Test Runtime

**Files:**
- Modify: `pom.xml`
- Create: `src/test/resources/application-test.properties`
- Modify: `src/test/java/com/nyasha/store/StoreApplicationTests.java`

- [x] **Step 1: Write the failing context-load test profile**

Replace `src/test/java/com/nyasha/store/StoreApplicationTests.java` with:

```java
package com.nyasha.store;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StoreApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [x] **Step 2: Run the test to verify the current runtime gap**

Run:

```bash
./mvnw -q test -Dtest=StoreApplicationTests
```

Expected before this task is complete: FAIL because the test profile is not configured and/or H2 is unavailable.

- [x] **Step 3: Add H2 as a test dependency**

In `pom.xml`, add this dependency inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [x] **Step 4: Add test datasource configuration**

Create `src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:store_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect

spring.main.allow-bean-definition-overriding=true
logging.level.org.springframework.security=warn
```

- [x] **Step 5: Run the context test again**

Run:

```bash
./mvnw -q test -Dtest=StoreApplicationTests
```

Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add pom.xml src/test/resources/application-test.properties src/test/java/com/nyasha/store/StoreApplicationTests.java
git commit -m "test: add isolated Spring Boot test profile"
```

---

### Task 2: Stop Mutating User Entities For API Responses

**Files:**
- Create: `src/main/java/com/nyasha/store/dtos/UserResponse.java`
- Modify: `src/main/java/com/nyasha/store/controllers/UserController.java`
- Test: `src/test/java/com/nyasha/store/controllers/UserControllerTest.java`

- [x] **Step 1: Write the failing controller test**

Create `src/test/java/com/nyasha/store/controllers/UserControllerTest.java`:

```java
package com.nyasha.store.controllers;

import com.nyasha.store.dtos.LoginRequest;
import com.nyasha.store.dtos.UserResponse;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserController controller = new UserController(userService);

    @Test
    void registerReturnsSanitizedUserWithoutMutatingEntityPassword() {
        User savedUser = user(1L, "Nyasha", "nyasha@example.com", "hashed-password");
        when(userService.createUser(savedUser)).thenReturn(savedUser);

        ResponseEntity<UserResponse> response = controller.createUser(savedUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(new UserResponse(1L, "Nyasha", "nyasha@example.com", null));
        assertThat(savedUser.getHashedPassword()).isEqualTo("hashed-password");
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() {
        LoginRequest request = new LoginRequest("nyasha@example.com", "bad-password");
        when(userService.authenticateUser("nyasha@example.com", "bad-password")).thenReturn(Optional.empty());

        ResponseEntity<UserResponse> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void listUsersReturnsSanitizedResponses() {
        when(userService.getAllUsers()).thenReturn(List.of(user(1L, "Nyasha", "nyasha@example.com", "hash")));

        List<UserResponse> users = controller.getAllUsers(null);

        assertThat(users).containsExactly(new UserResponse(1L, "Nyasha", "nyasha@example.com", null));
    }

    private User user(Long id, String name, String email, String password) {
        User user = new User();
        user.setUserId(id);
        user.setName(name);
        user.setEmail(email);
        user.setHashedPassword(password);
        return user;
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run:

```bash
./mvnw -q test -Dtest=UserControllerTest
```

Expected: FAIL because `UserController` does not have a constructor accepting `UserService`, and `UserResponse` does not exist.

- [x] **Step 3: Add the user response DTO**

Create `src/main/java/com/nyasha/store/dtos/UserResponse.java`:

```java
package com.nyasha.store.dtos;

import com.nyasha.store.entities.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String name,
        String email,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}
```

- [x] **Step 4: Refactor `UserController` to constructor injection and DTO responses**

Replace `src/main/java/com/nyasha/store/controllers/UserController.java` with:

```java
package com.nyasha.store.controllers;

import com.nyasha.store.dtos.LoginRequest;
import com.nyasha.store.dtos.UserResponse;
import com.nyasha.store.entities.User;
import com.nyasha.store.services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@CrossOrigin({"http://localhost:3000", "http://localhost:4200"})
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> createUser(@RequestBody User user) {
        User created = userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest loginRequest) {
        return userService.authenticateUser(loginRequest.getEmail(), loginRequest.getPassword())
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<UserResponse> getAllUsers(@RequestParam(required = false) String search) {
        List<User> users = search != null && !search.isBlank()
                ? userService.searchUsers(search)
                : userService.getAllUsers();

        return users.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/search")
    public List<UserResponse> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query).stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return UserResponse.from(userService.updateUser(id, userDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [x] **Step 5: Run the controller test**

Run:

```bash
./mvnw -q test -Dtest=UserControllerTest
```

Expected: PASS.

- [x] **Step 6: Run all tests**

Run:

```bash
./mvnw -q test
```

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/nyasha/store/dtos/UserResponse.java src/main/java/com/nyasha/store/controllers/UserController.java src/test/java/com/nyasha/store/controllers/UserControllerTest.java
git commit -m "fix: return sanitized user response DTOs"
```

---

### Task 3: Add Product Index Unit Coverage

**Files:**
- Test: `src/test/java/com/nyasha/store/utils/ProductIndexTest.java`

- [x] **Step 1: Write ProductIndex tests**

Create `src/test/java/com/nyasha/store/utils/ProductIndexTest.java`:

```java
package com.nyasha.store.utils;

import com.nyasha.store.entities.Category;
import com.nyasha.store.entities.Product;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIndexTest {

    private final ProductIndex index = new ProductIndex();

    @Test
    void searchByTextReturnsProductsMatchingNameOrDescriptionTokens() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics and fast SSD", "SKU-1", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByText("rtx")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByText("gaming")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void blankSearchesReturnEmptyResults() {
        index.insert(product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers")));

        assertThat(index.searchByText("")).isEmpty();
        assertThat(index.searchByText(null)).isEmpty();
        assertThat(index.searchByPrefix(" ")).isEmpty();
        assertThat(index.searchByCategory(null)).isEmpty();
    }

    @Test
    void searchByPrefixMatchesNameAndSku() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics", "GL-100", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByPrefix("gam")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByPrefix("gl")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void categorySearchUsesCategoryIds() {
        Product laptop = product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers"));
        index.insert(laptop);

        assertThat(index.searchByCategory("10")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByCategory("999")).isEmpty();
    }

    @Test
    void updateRemovesOldTermsAndAddsNewTerms() {
        Product oldProduct = product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers"));
        Product updatedProduct = product(1L, "Office Monitor", "4K display", "SKU-2", category(11L, "Displays"));

        index.insert(oldProduct);
        index.update(oldProduct, updatedProduct);

        assertThat(index.searchByText("gaming")).isEmpty();
        assertThat(index.searchByText("office")).extracting(Product::getProductId).containsExactly(1L);
        assertThat(index.searchByCategory("10")).isEmpty();
        assertThat(index.searchByCategory("11")).extracting(Product::getProductId).containsExactly(1L);
    }

    @Test
    void rebuildClearsStaleProducts() {
        index.insert(product(1L, "Gaming Laptop", "RTX graphics", "SKU-1", category(10L, "Computers")));

        index.rebuild(List.of(product(2L, "Office Monitor", "4K display", "SKU-2", category(11L, "Displays"))));

        assertThat(index.searchByText("gaming")).isEmpty();
        assertThat(index.searchByText("office")).extracting(Product::getProductId).containsExactly(2L);
    }

    private Product product(Long id, String name, String description, String sku, Category category) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setDescription(description);
        product.setSku(sku);
        product.setBasePrice(100.0);
        product.setCategories(new HashSet<>(Set.of(category)));
        return product;
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        return category;
    }
}
```

- [x] **Step 2: Run the ProductIndex tests**

Run:

```bash
./mvnw -q test -Dtest=ProductIndexTest
```

Expected: PASS. If any test fails because result ordering is unstable, preserve the behavior and change only the assertion to use `containsExactlyInAnyOrder`.

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/nyasha/store/utils/ProductIndexTest.java
git commit -m "test: cover product search index behavior"
```

---

### Task 4: Add User Index Unit Coverage

**Files:**
- Test: `src/test/java/com/nyasha/store/utils/UserIndexTest.java`

- [x] **Step 1: Write UserIndex tests**

Create `src/test/java/com/nyasha/store/utils/UserIndexTest.java`:

```java
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
```

- [x] **Step 2: Run the UserIndex tests**

Run:

```bash
./mvnw -q test -Dtest=UserIndexTest
```

Expected: PASS.

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/nyasha/store/utils/UserIndexTest.java
git commit -m "test: cover user search index behavior"
```

---

### Task 5: Add Product Service Unit Coverage

**Files:**
- Test: `src/test/java/com/nyasha/store/services/ProductServiceTest.java`

- [x] **Step 1: Write ProductService tests**

Create `src/test/java/com/nyasha/store/services/ProductServiceTest.java`:

```java
package com.nyasha.store.services;

import com.nyasha.store.entities.Product;
import com.nyasha.store.repositories.ProductRepository;
import com.nyasha.store.utils.ProductIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductIndex productIndex;

    @InjectMocks
    private ProductService productService;

    @Test
    void initializeIndexRebuildsFromRepositoryProducts() {
        Product product = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.findAll()).thenReturn(List.of(product));
        when(productRepository.count()).thenReturn(1L);

        productService.initializeIndex();

        verify(productIndex).rebuild(List.of(product));
    }

    @Test
    void createProductValidatesPayloadBeforeRepositoryWrite() {
        Product invalid = product(null, "", "No name", "SKU-1", 100.0);

        assertThatThrownBy(() -> productService.createProduct(invalid))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product name is required");

        verifyNoInteractions(productRepository);
        verifyNoInteractions(productIndex);
    }

    @Test
    void createProductSavesAndIndexesValidProduct() {
        Product product = product(null, "Laptop", "Fast laptop", "SKU-1", 100.0);
        Product saved = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.save(product)).thenReturn(saved);

        Product result = productService.createProduct(product);

        assertThat(result.getProductId()).isEqualTo(1L);
        verify(productIndex).insert(saved);
    }

    @Test
    void getProductsByNumericCategoryUsesRepositoryQuery() {
        Product product = product(1L, "Laptop", "Fast laptop", "SKU-1", 100.0);
        when(productRepository.findByCategoryId(10L)).thenReturn(List.of(product));

        List<Product> results = productService.getProductsByCategory("10");

        assertThat(results).containsExactly(product);
        verify(productRepository).findByCategoryId(10L);
    }

    @Test
    void updateProductFailsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, product(null, "Laptop", "Fast", "SKU-1", 100.0)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product update failed");
    }

    private Product product(Long id, String name, String description, String sku, Double basePrice) {
        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setDescription(description);
        product.setSku(sku);
        product.setBasePrice(basePrice);
        return product;
    }
}
```

- [x] **Step 2: Run the ProductService tests**

Run:

```bash
./mvnw -q test -Dtest=ProductServiceTest
```

Expected: PASS.

- [x] **Step 3: Commit**

```bash
git add src/test/java/com/nyasha/store/services/ProductServiceTest.java
git commit -m "test: cover product service stabilization"
```

---

### Task 6: Add Project README And Phase Memory

**Files:**
- Create or modify: `README.md`
- Modify: `docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md`

- [x] **Step 1: Add README**

Create `README.md`:

```markdown
# Optimizing Search Algorithms In E-commerce Platforms - Backend

This project is being rebuilt into a production-style search benchmarking lab using e-commerce catalog data.

## Goal

Compare search approaches across latency, indexing throughput, freshness, and relevance:

1. SQL LIKE
2. PostgreSQL full-text search
3. Custom in-memory inverted index
4. OpenSearch BM25

## Current Phase

Current phase: **Phase 0 - Stabilize Current Backend**

Phase 0 makes the existing Spring Boot backend buildable, testable, documented, and safe enough to support the search benchmarking lab.

## Phase Roadmap

| Phase | Name | Status |
| --- | --- | --- |
| Phase 0 | Stabilize Current Backend | Current |
| Phase 1 | Search Abstraction | Next |
| Phase 2 | Infrastructure | Upcoming |
| Phase 3 | Event-Driven Indexing | Upcoming |
| Phase 4 | Benchmarking | Upcoming |
| Phase 5 | Verification And Documentation | Upcoming |

## Tech Stack

- Java 21
- Spring Boot 3.4.3
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- H2 for tests
- Maven Wrapper

## Prerequisites

```bash
java -version
```

Expected: Java 21.

## Run Tests

```bash
./mvnw test
```

## Run The API Locally

Set PostgreSQL connection values if your local database differs from the defaults:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/store_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=Gyver
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

## Design Docs

- `docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md`
- `docs/superpowers/plans/2026-05-21-phase-0-stabilize-current-backend.md`
```

- [x] **Step 2: Confirm the design spec still marks Phase 0 as current**

Check:

```bash
rg -n "Current phase: Phase 0|Phase 1" docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md README.md
```

Expected: both files identify Phase 0 as current and Phase 1 as next.

- [x] **Step 3: Commit**

```bash
git add README.md docs/superpowers/specs/2026-05-21-search-benchmarking-lab-design.md
git commit -m "docs: document phase zero backend stabilization"
```

---

### Task 7: Full Verification And PR Update

**Files:**
- No code files expected unless verification exposes a defect.

- [x] **Step 1: Run full tests**

Run:

```bash
./mvnw test
```

Expected: BUILD SUCCESS.

- [x] **Step 2: Verify working tree**

Run:

```bash
git status --short --branch
```

Expected:

```text
## complete-backend-stabilization
```

No modified or untracked files should appear.

- [x] **Step 3: Push the branch**

Run:

```bash
git push
```

Expected: branch pushes to `origin/complete-backend-stabilization`.

- [x] **Step 4: Check PR status**

Run:

```bash
gh pr view 1 --json url,state,mergeable,reviewDecision,statusCheckRollup
```

Expected:

- `state` is `OPEN`.
- `mergeable` is `MERGEABLE` or `UNKNOWN` while GitHub recalculates.
- Any required checks are passing or still pending, not failed.

- [x] **Step 5: Add a PR comment with Phase 0 verification**

Run:

```bash
gh pr comment 1 --body "Phase 0 plan added and backend stabilization verified locally with \`./mvnw test\`."
```

Expected: comment is added to PR #1.

If Java is unavailable in the current environment, do not claim verification passed. Comment instead:

```bash
gh pr comment 1 --body "Phase 0 plan added. Local test execution is blocked in this environment because Java 21/JAVA_HOME is not available; tests should be run once JDK 21 is installed."
```

## Self-Review Checklist

- Spec coverage: This plan covers Phase 0 only, matching the current phase in the approved design spec.
- No placeholders: Every code change has concrete file paths, exact code, commands, and expected outcomes.
- Type consistency: `UserResponse` is introduced before `UserController` uses it; test method signatures match the planned controller signatures.
- Scope control: Kafka, OpenSearch, Docker Compose, benchmark entities, and report generation are intentionally excluded until later phases.
