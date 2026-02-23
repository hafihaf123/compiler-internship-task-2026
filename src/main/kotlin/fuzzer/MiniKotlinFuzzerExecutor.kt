package fuzzer

import MiniKotlinLexer
import MiniKotlinParser
import compiler.MiniKotlinCompiler
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.example.compiler.CompilationResult
import org.example.compiler.ExecutionResult
import org.example.compiler.JavaRuntimeCompiler
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class MiniKotlinFuzzerExecutor {
    private var engine = ScriptEngineManager().getEngineByExtension("kts")!!

    fun resetEngine() {
        engine = ScriptEngineManager().getEngineByExtension("kts")!!
    }

    private fun parseString(source: String): MiniKotlinParser.ProgramContext {
        val input = CharStreams.fromString(source)
        val lexer = MiniKotlinLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = MiniKotlinParser(tokens)
        return parser.program()
    }

    @Suppress("DuplicatedCode")
    private fun resolveStdlibPath(): Path? {
        val devPath = Paths.get("build", "stdlib")
        if (devPath.toFile().exists()) {
            val stdlibJar =
                devPath.toFile().listFiles()?.firstOrNull { it.name.startsWith("stdlib") && it.name.endsWith(".jar") }
            if (stdlibJar != null) return stdlibJar.toPath()
        }
        return null
    }

    fun executeMiniKotlin(sourceCode: String): Pair<CompilationResult, ExecutionResult?> {
        val program = parseString(sourceCode)
        val compiler = MiniKotlinCompiler()
        val javaCode = compiler.compile(program)
        val tmpDir = createTempDirectory()
        val javaFile = tmpDir.resolve("MiniProgram.java")
        javaFile.toFile().deleteOnExit()
        tmpDir.toFile().deleteOnExit()
        javaFile.writeText(javaCode)
        val javaCompiler = JavaRuntimeCompiler()
        val stdlibPath = resolveStdlibPath()

        return javaCompiler.compileAndExecute(javaFile, stdlibPath)
    }

    fun executeKotlin(sourceCode: String): ExecutionResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val oldStdout = System.out
        val oldStderr = System.err
        System.setOut(PrintStream(stdout))
        System.setErr(PrintStream(stderr))
        val result = try {
            val suppresions =
                "@file:Suppress(\"UNUSED_VARIABLE\", \"ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE\", \"UNUSED_VALUE\", \"UNUSED_PARAMETER\", \"SENSELESS_COMPARISON\", \"VARIABLE_WITH_REDUNDANT_INITIALIZER\")\n"
            engine.eval("$suppresions$sourceCode\n\nmain()")
            ExecutionResult.Success(stdout.toString().trim(), stderr.toString().trim())
        } catch (exception: ScriptException) {
            ExecutionResult.Failure(exception.toString(), exception)
        } finally {
            System.setOut(oldStdout)
            System.setErr(oldStderr)
        }

        return result
    }
}