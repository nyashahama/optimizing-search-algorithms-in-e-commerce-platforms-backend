package com.nyasha.store.configurations;

import com.nyasha.store.benchmark.controllers.BenchmarkController;
import com.nyasha.store.controllers.AddressController;
import com.nyasha.store.controllers.CartController;
import com.nyasha.store.controllers.CategoryController;
import com.nyasha.store.controllers.CheckoutController;
import com.nyasha.store.controllers.IndexController;
import com.nyasha.store.controllers.InventoryController;
import com.nyasha.store.controllers.OrderController;
import com.nyasha.store.controllers.PaymentController;
import com.nyasha.store.controllers.ProductController;
import com.nyasha.store.controllers.ReturnsController;
import com.nyasha.store.controllers.ReviewController;
import com.nyasha.store.controllers.SearchController;
import com.nyasha.store.controllers.SupplierController;
import com.nyasha.store.controllers.UserController;
import com.nyasha.store.controllers.WishlistController;
import com.nyasha.store.observability.OperationsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(controllers = {
        CartController.class,
        CheckoutController.class,
        OrderController.class,
        ReturnsController.class,
        WishlistController.class,
        ReviewController.class,
        InventoryController.class,
        SupplierController.class,
        PaymentController.class,
        AddressController.class,
        UserController.class,
        SearchController.class,
        ProductController.class,
        CategoryController.class,
        BenchmarkController.class,
        OperationsController.class,
        IndexController.class
}, properties = {
        "spring.security.user.name=test-user",
        "spring.security.user.password=test-password",
        "security.rate-limit.enabled=false"
})
@Import(SecurityConfig.class)
class EndpointContractCoverageTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyDocumentedEndpointIsBoundToAControllerAndAllControllersAreDocumented() {
        Set<String> configuredEndpoints = normalizedConfiguredRoutes();
        Set<String> matrixEndpoints = normalizedMatrixEndpoints();

        Set<String> missingFromMatrix = new TreeSet<>(configuredEndpoints);
        missingFromMatrix.removeAll(matrixEndpoints);
        assertThat(missingFromMatrix)
                .as("Controller endpoints are present in the auth matrix")
                .isEmpty();

        Set<String> missingFromControllers = new TreeSet<>(matrixEndpoints);
        missingFromControllers.removeAll(configuredEndpoints);
        assertThat(missingFromControllers)
                .as("Auth matrix entries are represented by actual controller mappings")
                .isEmpty();
    }

    private Set<String> normalizedConfiguredRoutes() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> isEcommerceApiController(entry.getValue()))
                .flatMap(entry -> canonicalizeMappedRoutes(entry.getKey()).stream())
                .collect(Collectors.toSet());
    }

    private Set<String> canonicalizeMappedRoutes(RequestMappingInfo mappingInfo) {
        return mappingInfo.getMethodsCondition().getMethods().stream()
                .flatMap(method -> mappingInfo.getPatternValues().stream()
                        .map(pattern -> canonicalRoute(method.name(), normalizeMappedPath(pattern)))
                )
                .collect(Collectors.toSet());
    }

    private Set<String> normalizedMatrixEndpoints() {
        return EndpointAuthorizationMatrixTest.endpointMatrix().stream()
                .map(entry -> canonicalRoute(entry.method(), entry.path()))
                .collect(Collectors.toSet());
    }

    private boolean isEcommerceApiController(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();
        return packageName.startsWith("com.nyasha.store.controllers")
                || packageName.startsWith("com.nyasha.store.benchmark.controllers")
                || packageName.startsWith("com.nyasha.store.observability");
    }

    private static String canonicalRoute(String method, String path) {
        return method + " " + normalizePathSegments(path);
    }

    private static String normalizePathSegments(String path) {
        return Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(segment -> segment.matches("\\d+") || segment.contains(".") ? "{id}" : segment)
                .collect(Collectors.joining("/", "/", ""));
    }

    private static String normalizeMappedPath(String path) {
        return path.replaceAll("\\{[^/]+\\}", "{id}");
    }
}
