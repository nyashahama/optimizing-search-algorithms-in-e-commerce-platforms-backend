# Phase 1: Search Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

## Goal

Create a unified search API where SQL LIKE, PostgreSQL full-text search, in-memory inverted index, and OpenSearch can be addressed by one route and compared in one response.

## Task 1: Core Search Contracts

- [x] Add search engine enum with stable API labels and alias parsing.
- [x] Add normalized search query model (query + limit with defaults/caps).
- [x] Add standardized search result and response models.
- [x] Add search engine interface.

## Task 2: Engine Implementations

- [x] Implement SQL LIKE engine using pageable repository query.
- [x] Implement in-memory engine over `ProductIndex`.
- [x] Add PostgreSQL FTS placeholder engine that returns a stable unsupported response.
- [x] Add OpenSearch placeholder engine that returns a stable unsupported response.

## Task 3: Search Service

- [x] Register all engines in a service map for centralized routing.
- [x] Add single-engine search API method (`/api/search`).
- [x] Add compare method that invokes all engines and returns response metadata.

## Task 4: API Wiring

- [x] Add `SearchController` with `/api/search` and `/api/search/compare`.
- [x] Keep `/api/products/search` working by routing to in-memory search.

## Task 5: Repository Support

- [x] Add pageable repository method for SQL LIKE searches.

## Task 6: Tests

- [x] Add unit tests for engine availability, search behavior, and compare aggregation.
