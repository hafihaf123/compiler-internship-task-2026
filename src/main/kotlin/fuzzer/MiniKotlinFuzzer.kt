package fuzzer

import org.example.compiler.CompilationResult
import org.example.compiler.ExecutionResult
import java.io.File

class MiniKotlinFuzzer(
    val iterations: Int = 1000,
    val generator: MiniKotlinFuzzerGenerator = MiniKotlinFuzzerGenerator(202859949),
    val executor: MiniKotlinFuzzerExecutor = MiniKotlinFuzzerExecutor()
)

fun main() {
    println("Starting Differential Fuzzer...")
    val fuzzer = MiniKotlinFuzzer()
    var failures = 0

    with(fuzzer) {
        for (i in 1..iterations) {
            val sourceCode = generator.generateProgram()

            try {
                executor.resetEngine()

                val kotlinResult = executor.executeKotlin(sourceCode)
                val (compilationResult, executionResult) = executor.executeMiniKotlin(sourceCode)

                if (compilationResult is CompilationResult.Failure) {
                    System.err.println("\n❌ JAVA COMPILATION FAILED ON BATCH")
                    File("FailingBatch.mini").writeText(sourceCode)
                    System.err.println("Errors: ${compilationResult.errors}")
                    failures++
                    break
                }

                if (executionResult is ExecutionResult.Failure) {
                    System.err.println("\n❌ JAVA EXECUTION FAILED ON BATCH!")
                    System.err.println("Error: ${executionResult.error}")
                    failures++
                    break
                }

                if (kotlinResult is ExecutionResult.Failure) {
                    System.err.println("\n❌ KOTLIN EXECUTION FAILED ON BATCH!")
                    System.err.println("Error: ${kotlinResult.error}")
                    failures++
                    continue
                }

                val myOutput = (executionResult as ExecutionResult.Success).stdout.trim()
                val kotlinOutput = (kotlinResult as ExecutionResult.Success).stdout.trim()

                if (kotlinOutput != myOutput) {
                    System.err.println("❌ FUZZING FAILURE ON ITERATION $i!")
                    System.err.println("Seed: ${generator.hashCode()}") // Useful for reproducing
                    System.err.println("--- Source Code ---")
                    System.err.println(sourceCode)
                    System.err.println("--- Kotlin Output ---")
                    System.err.println(kotlinOutput)
                    System.err.println("--- Transpiler Output ---")
                    System.err.println(myOutput)
                    failures++
                    break
                } else {
                    print("-")
                    if (i % 100 == 0) println(" ($i/$iterations)")
                }

            } catch (e: Exception) {
                println()
                println("Exception during iteration $i: ${e.message}")
                failures++
                println()
                println(sourceCode)
                break
            }
        }
        println("Fuzzing complete. $iterations iterations passed with $failures failures.")
    }
}
