.PHONY: help build test e2e-test validate clean

# Default target
help:
	@echo "Forge Build Targets:"
	@echo "  make build      - Build all modules (skips tests and checkstyle)"
	@echo "  make test       - Run unit tests"
	@echo "  make e2e-test   - Run end-to-end tests"
	@echo "  make validate   - Run all tests (unit + e2e) and checks"
	@echo "  make clean      - Clean build artifacts"
	@echo ""
	@echo "Before committing, always run 'make validate' and include results in commit message!"

# Build the project (fast build for development)
build:
	@echo "=== Building Forge ==="
	mvn -pl forge-core,forge-game,forge-ai,forge-headless -am package -DskipTests -Dcheckstyle.skip=true

# Run Maven unit tests (only for TUI-related modules)
test:
	@echo "=== Running Unit Tests ==="
	mvn -pl forge-core,forge-game,forge-ai,forge-headless -am test

# Run end-to-end tests
e2e-test: build
	@echo "=== Running End-to-End Tests ==="
	@bash forge-headless/test_scripts/test_counterspell.sh

# Validate everything before commit (unit tests + e2e tests)
validate: build test e2e-test
	@echo ""
	@echo "======================================"
	@echo "All validation checks passed!"
	@echo "======================================"
	@echo ""
	@echo "Add this to your commit message:"
	@echo "  Validation: make validate passed (unit tests + e2e tests)"

# Clean build artifacts
clean:
	@echo "=== Cleaning Build Artifacts ==="
	mvn clean
