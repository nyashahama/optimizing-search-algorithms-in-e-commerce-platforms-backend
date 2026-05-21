package com.nyasha.store.benchmark.services;

import com.nyasha.store.benchmark.models.BenchmarkJudgment;
import com.nyasha.store.benchmark.models.BenchmarkQuery;
import com.nyasha.store.benchmark.models.BenchmarkQuerySet;
import com.nyasha.store.benchmark.repositories.BenchmarkJudgmentRepository;
import com.nyasha.store.benchmark.repositories.BenchmarkQuerySetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Component
public class BenchmarkSeedService implements CommandLineRunner {

    private final BenchmarkQuerySetRepository querySetRepository;
    private final BenchmarkJudgmentRepository judgmentRepository;
    private final ObjectMapper objectMapper;

    public BenchmarkSeedService(
            BenchmarkQuerySetRepository querySetRepository,
            BenchmarkJudgmentRepository judgmentRepository,
            ObjectMapper objectMapper
    ) {
        this.querySetRepository = querySetRepository;
        this.judgmentRepository = judgmentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        if (querySetRepository.count() > 0) {
            return;
        }

        BenchmarkSeedData seedData = readSeedData().orElseGet(BenchmarkSeedService::defaultSeedData);
        for (SeedQuerySet seedQuerySet : seedData.querySets()) {
            BenchmarkQuerySet querySet = toEntity(seedQuerySet);
            BenchmarkQuerySet savedSet = querySetRepository.save(querySet);
            seedQuerySet.judgments().forEach(judgment -> judgmentRepository.save(
                    toJudgment(savedSet, judgment)
            ));
        }
    }

    private BenchmarkQuerySet toEntity(SeedQuerySet seedQuerySet) {
        BenchmarkQuerySet querySet = new BenchmarkQuerySet();
        querySet.setName(seedQuerySet.name());
        querySet.setDescription(seedQuerySet.description());

        querySet.setQueries(new LinkedList<>());
        for (SeedQuery query : seedQuerySet.queries()) {
            querySet.getQueries().add(createQuery(querySet, query.text(), query.position()));
        }
        return querySet;
    }

    private BenchmarkQuery createQuery(BenchmarkQuerySet set, String queryText, int position) {
        BenchmarkQuery query = new BenchmarkQuery();
        query.setQuerySet(set);
        query.setQueryText(queryText);
        query.setPosition(position);
        return query;
    }

    private BenchmarkJudgment toJudgment(BenchmarkQuerySet set, SeedJudgment seedJudgment) {
        BenchmarkJudgment judgment = new BenchmarkJudgment();
        judgment.setQuerySet(set);
        judgment.setQueryText(seedJudgment.queryText());
        judgment.setProductId(seedJudgment.productId());
        judgment.setRelevance(seedJudgment.relevance());
        return judgment;
    }

    private Optional<BenchmarkSeedData> readSeedData() {
        try (InputStream in = getClass().getResourceAsStream("/benchmark/seed/benchmark-fixtures.json")) {
            if (in == null) {
                return Optional.empty();
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(objectMapper.readValue(json, BenchmarkSeedData.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static BenchmarkSeedData defaultSeedData() {
        return new BenchmarkSeedData(List.of(
                new SeedQuerySet(
                        "electronics-basic",
                        "Starter query set for e-commerce search benchmarking",
                        List.of(
                                new SeedQuery("wireless headphones", 1),
                                new SeedQuery("gaming laptop", 2),
                                new SeedQuery("running shoes", 3)
                        ),
                        List.of(
                                new SeedJudgment("wireless headphones", 1L, 3),
                                new SeedJudgment("gaming laptop", 2L, 3),
                                new SeedJudgment("running shoes", 3L, 3)
                        )
                )
        ));
    }

    private record BenchmarkSeedData(List<SeedQuerySet> querySets) {}
    private record SeedQuerySet(
            String name,
            String description,
            List<SeedQuery> queries,
            List<SeedJudgment> judgments
    ) {}
    private record SeedQuery(String text, int position) {}
    private record SeedJudgment(String queryText, Long productId, int relevance) {}
}
