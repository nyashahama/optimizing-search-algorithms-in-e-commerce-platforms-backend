CREATE TABLE roles (
    role_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE suppliers (
    supplier_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    contact_info VARCHAR(255),
    address VARCHAR(255)
);

CREATE TABLE categories (
    category_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    parent_category_id BIGINT,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories (category_id)
);

CREATE TABLE products (
    product_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description TEXT,
    base_price DOUBLE PRECISION,
    sku VARCHAR(255),
    brand VARCHAR(255),
    attributes VARCHAR(255),
    inventory_status VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE,
    updated_at TIMESTAMP WITHOUT TIME ZONE,
    supplier_id BIGINT,
    CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (supplier_id)
);

CREATE TABLE product_variants (
    variant_id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    size VARCHAR(255),
    color VARCHAR(255),
    material VARCHAR(255),
    price_adjustment DOUBLE PRECISION,
    sku VARCHAR(255),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (product_id)
);

CREATE TABLE product_categories (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT fk_product_categories_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_product_categories_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT pk_product_categories PRIMARY KEY (product_id, category_id)
);

CREATE TABLE inventory (
    inventory_id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    quantity INTEGER,
    location VARCHAR(255),
    reorder_threshold INTEGER,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_inventory_variant FOREIGN KEY (variant_id) REFERENCES product_variants (variant_id),
    CONSTRAINT uq_inventory_product_variant UNIQUE (product_id, variant_id)
);

CREATE TABLE discounts (
    discount_id BIGSERIAL PRIMARY KEY,
    code VARCHAR(255),
    type VARCHAR(255),
    discount_value DOUBLE PRECISION,
    valid_from TIMESTAMP WITHOUT TIME ZONE,
    valid_to TIMESTAMP WITHOUT TIME ZONE,
    max_uses INTEGER
);

CREATE TABLE discount_products (
    discount_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT fk_discount_products_discount FOREIGN KEY (discount_id) REFERENCES discounts (discount_id),
    CONSTRAINT fk_discount_products_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT pk_discount_products PRIMARY KEY (discount_id, product_id)
);

CREATE TABLE discount_categories (
    discount_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT fk_discount_categories_discount FOREIGN KEY (discount_id) REFERENCES discounts (discount_id),
    CONSTRAINT fk_discount_categories_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT pk_discount_categories PRIMARY KEY (discount_id, category_id)
);

CREATE TABLE carts (
    cart_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_carts_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE cart_items (
    cart_item_id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    quantity INTEGER,
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (cart_id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (variant_id),
    CONSTRAINT uq_cart_items_unique_item UNIQUE (cart_id, product_id, variant_id)
);

CREATE TABLE wishlists (
    wishlist_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_wishlists_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE wishlist_items (
    wishlist_item_id BIGSERIAL PRIMARY KEY,
    wishlist_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    CONSTRAINT fk_wishlist_items_wishlist FOREIGN KEY (wishlist_id) REFERENCES wishlists (wishlist_id),
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_wishlist_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (variant_id),
    CONSTRAINT uq_wishlist_items_unique_item UNIQUE (wishlist_id, product_id, variant_id)
);

CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_date TIMESTAMP WITHOUT TIME ZONE,
    total_amount DOUBLE PRECISION,
    status VARCHAR(255),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    quantity INTEGER,
    price_at_purchase DOUBLE PRECISION,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (variant_id)
);

CREATE TABLE payments (
    payment_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    method VARCHAR(255),
    transaction_id VARCHAR(255),
    amount DOUBLE PRECISION,
    status VARCHAR(255),
    timestamp TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE TABLE shipping_info (
    shipping_id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    address_id BIGINT,
    tracking_number VARCHAR(255),
    carrier VARCHAR(255),
    status VARCHAR(255),
    estimated_delivery TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_shipping_info_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE TABLE returns (
    return_id BIGSERIAL PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    reason VARCHAR(255),
    status VARCHAR(255),
    refund_amount DOUBLE PRECISION,
    processed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_returns_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (order_item_id)
);

CREATE TABLE reviews (
    review_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    rating INTEGER,
    comment VARCHAR(255),
    date TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE addresses (
    address_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    street VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    zip VARCHAR(255),
    country VARCHAR(255),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (role_id),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id)
);

CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    key_value VARCHAR(255) NOT NULL,
    operation VARCHAR(255) NOT NULL,
    user_id BIGINT,
    order_id BIGINT,
    request_hash TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_idempotency_keys UNIQUE (key_value, operation)
);

CREATE TABLE indexing_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    event_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITHOUT TIME ZONE,
    retry_count INTEGER NOT NULL,
    error_message VARCHAR(2048),
    payload TEXT
);

CREATE TABLE benchmark_query_sets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE benchmark_queries (
    id BIGSERIAL PRIMARY KEY,
    query_set_id BIGINT,
    query_text VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT fk_benchmark_queries_query_set FOREIGN KEY (query_set_id) REFERENCES benchmark_query_sets (id)
);

CREATE TABLE benchmark_runs (
    id BIGSERIAL PRIMARY KEY,
    query_set_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    total_queries INTEGER,
    total_engines INTEGER,
    duration_ms BIGINT,
    throughput_queries_per_second DOUBLE PRECISION,
    latency_min_ms BIGINT,
    latency_p50_ms BIGINT,
    latency_p95_ms BIGINT,
    latency_p99_ms BIGINT,
    latency_avg_ms DOUBLE PRECISION,
    freshness_p50_ms BIGINT,
    freshness_p95_ms BIGINT,
    freshness_p99_ms BIGINT,
    freshness_avg_ms DOUBLE PRECISION,
    report_directory VARCHAR(255),
    CONSTRAINT fk_benchmark_runs_query_set FOREIGN KEY (query_set_id) REFERENCES benchmark_query_sets (id)
);

CREATE TABLE benchmark_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    query_text VARCHAR(255) NOT NULL,
    engine VARCHAR(255) NOT NULL,
    latency_ms BIGINT,
    result_count INTEGER,
    returned_count INTEGER,
    top_result_product_ids VARCHAR(2048),
    precision_at_k DOUBLE PRECISION,
    recall_at_k DOUBLE PRECISION,
    mrr_at_k DOUBLE PRECISION,
    ndcg_at_k DOUBLE PRECISION,
    error_message VARCHAR(1024),
    CONSTRAINT fk_benchmark_results_run FOREIGN KEY (run_id) REFERENCES benchmark_runs (id)
);

CREATE TABLE benchmark_judgments (
    id BIGSERIAL PRIMARY KEY,
    query_set_id BIGINT NOT NULL,
    query_text VARCHAR(255) NOT NULL,
    product_id BIGINT NOT NULL,
    relevance INTEGER NOT NULL,
    CONSTRAINT fk_benchmark_judgments_query_set FOREIGN KEY (query_set_id) REFERENCES benchmark_query_sets (id),
    CONSTRAINT uq_judgment UNIQUE (query_set_id, query_text, product_id)
);
