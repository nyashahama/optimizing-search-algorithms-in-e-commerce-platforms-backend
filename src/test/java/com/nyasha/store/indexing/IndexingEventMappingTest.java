package com.nyasha.store.indexing;

import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndexingEventMappingTest {

    @Test
    void payloadUsesPlainTextColumnInsteadOfLobStream() throws Exception {
        assertThat(IndexingEvent.class.getDeclaredField("payload").isAnnotationPresent(Lob.class))
                .isFalse();
    }
}
