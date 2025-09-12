# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

partial-cps is a Clojure/ClojureScript library for continuation-passing style (CPS) transformations. It provides lightweight coroutines and async/await functionality without the overhead of scheduling frameworks like core.async.

## Development Commands

### Building
```bash
# Build JAR
clojure -T:build ci

# Install locally
clojure -T:build install

# Deploy to Clojars
clojure -T:build deploy
```

### Testing
```bash
# Run Clojure tests
clojure -T:build test

# Alternative: Run tests directly with test-runner
clojure -M:test -m cognitect.test-runner
```

### REPL
```bash
# Start REPL with CIDER support
clojure -M:repl
```

## Architecture

### Core Namespaces

1. **is.simm.partial-cps** - Public API entry point that re-exports key functions
2. **is.simm.partial-cps.ioc** - Inversion of Control implementation for CPS transformation
3. **is.simm.partial-cps.runtime** - Runtime execution and trampolining for coroutines
4. **is.simm.partial-cps.async** - Async/await implementation built on top of the CPS transform

### Key Concepts

**CPS Transformation**: The library transforms regular Clojure code into continuation-passing style at compile time using macros. This is done through:
- The `cps` macro that accepts breakpoints and transforms code blocks
- breakpoints that handle specific function calls (like `await`) during transformation
- The IOC namespace walks the AST and inverts control flow where needed

**Trampolining**: The runtime uses safe trampolining to avoid stack overflow while maintaining synchronous code sections for performance. Async operations only hit the JS event loop when the effect handler schedules it.

**Cross-platform Support**: Code is written in `.cljc` files with reader conditionals (`#?`) to handle platform differences between Clojure and ClojureScript.

## Code Conventions

- Use `.cljc` extension for cross-platform code
- Use reader conditionals `#?(:clj ... :cljs ...)` for platform-specific code
- Macros must be defined in Clojure (`:clj`) blocks even for ClojureScript usage
- Follow standard Clojure naming conventions (kebab-case for functions, PascalCase for records/types)
- Use `letfn` for local recursive functions
- Prefix internal/implementation functions with `-` or mark with `^:no-doc`

## Testing Approach

- Clojure tests use `clojure.test` and are run via cognitect test-runner
- ClojureScript tests exist but require shadow-cljs configuration to be completed
- Test files follow the pattern `<namespace>_test.clj[s]`