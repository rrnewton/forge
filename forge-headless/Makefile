# Makefile for Forge Headless TUI testing
#
# Targets:
#   make test     - Run fast Java unit tests (in-process, no subprocess overhead)
#   make validate - Run comprehensive e2e Python tests (with external agents)
#   make build    - Build the project
#   make clean    - Clean build artifacts

.PHONY: test validate build clean all

# Default target
all: build test

# Build the project with Maven
build:
	@echo "Building forge-headless..."
	cd .. && mvn clean package -DskipTests -Dcheckstyle.skip=true -pl forge-headless -am -q

# Run fast Java unit tests
test:
	@echo ""
	@echo "=========================================="
	@echo "Running Java Unit Tests (fast)"
	@echo "=========================================="
	cd .. && mvn test -Dcheckstyle.skip=true -pl forge-headless -am -q
	@echo ""
	@echo "✓ Java tests completed"

# Run comprehensive e2e tests with Python
validate: build
	@echo ""
	@echo "=========================================="
	@echo "Running End-to-End Validation Tests"
	@echo "=========================================="
	@echo "These tests spawn actual TUI processes with external agents."
	@echo "They are slower but test the full system integration."
	@echo ""
	python3 test_tui.py
	@echo ""
	@echo "✓ E2E validation completed"

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	cd .. && mvn clean -pl forge-headless -q
	@echo "✓ Clean completed"

# Help target
help:
	@echo "Forge Headless TUI Testing Makefile"
	@echo ""
	@echo "Available targets:"
	@echo "  make test     - Run fast Java unit tests (~10-20 seconds)"
	@echo "  make validate - Run comprehensive e2e Python tests (~1-2 minutes)"
	@echo "  make build    - Build the project with Maven"
	@echo "  make clean    - Clean build artifacts"
	@echo "  make all      - Build and run Java tests (default)"
	@echo ""
	@echo "Typical workflow:"
	@echo "  make test     # Quick feedback during development"
	@echo "  make validate # Comprehensive check before commit"
