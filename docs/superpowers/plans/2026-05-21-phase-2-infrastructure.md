# Phase 2: Infrastructure

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

## Goal

Add local infrastructure dependencies (PostgreSQL, Kafka, OpenSearch) with a reproducible startup path so the benchmarking features can run against real services.

## Task 1: Service Topology

- [x] Add Docker Compose with PostgreSQL 16, Kafka broker, ZooKeeper, and OpenSearch.
- [x] Add named volumes and configurable environment variables for local runs.
- [x] Add health checks for the core infrastructure services.

## Task 2: Local Runtime Profiles

- [x] Add `application-docker.properties` profile for containerized local services.
- [x] Externalize bootstrap URLs and credentials for container runtime (PostgreSQL, Kafka, OpenSearch).

## Task 3: Operational Docs

- [x] Add `.env.example` with all required defaults.
- [x] Update README with local infra startup commands and profile usage.

## Task 4: Phase Tracking

- [x] Update spec roadmap to reflect Phase 2 as current and Phase 1 complete.
