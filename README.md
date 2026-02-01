# MiniKotlin to Java CPS Compiler

This is an internship assignment for implementing a CPS-style (Continuation-Passing Style) compiler from MiniKotlin (a subset of Kotlin) to Java.

## Overview

The goal is to implement a compiler that translates MiniKotlin source code into Java, where all functions are expressed using continuation-passing style.

## Project Structure

- `samples/` - Example MiniKotlin programs
- `src/main/antlr/MiniKotlin.g4` - Grammar definition for MiniKotlin
- `src/main/kotlin/compiler/` - Compiler implementation
- `src/test/` - Testing framework
- `stdlib/` - Standard library with `Prelude` class containing CPS function examples

## Task

Implement the `MiniKotlinCompiler` to translate MiniKotlin to Java such that:

1. All functions use continuation-passing style
2. The semantics of operators follows Kotlin 

See `Prelude` in the stdlib for examples of how CPS functions should look.

## Building and Running

```bash
# Build the project
./gradlew build

# Run with default example
./gradlew run

# Run with a specific file
./gradlew run --args="samples/example.mini"

# Run tests
./gradlew test
```

## Evaluation

The task will be tested on a hidden set of tests.