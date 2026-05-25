#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cat <<MSG
Running endpoint readiness checks in containerized Maven (Java 21):
  - EndpointAuthorizationMatrixTest
  - EndpointContractCoverageTest
  - EndpointDocumentationAlignmentTest
  - OperationsReadinessEndpointTest
  - CommerceFlowSmokeIT
MSG

docker run --rm \
  -v "$ROOT_DIR":/workspace \
  -w /workspace \
  maven:3.9.6-eclipse-temurin-21 \
  sh -c "mvn -q -Dtest=EndpointAuthorizationMatrixTest,EndpointContractCoverageTest,EndpointDocumentationAlignmentTest,OperationsReadinessEndpointTest,CommerceFlowSmokeIT test"
