package fuzzer

import compiler.MiniKotlinFunction
import compiler.MiniKotlinParam
import compiler.MiniKotlinType
import compiler.UserDefinedFunction
import kotlin.random.Random

/**
 * A Semantic Fuzzer for MiniKotlin.
 * Generates random, type-correct code to find bugs in the CPS Transpiler.
 */
class MiniKotlinFuzzerGenerator(seed: Long = Random.nextLong()) {
    private val random = Random(seed)
    private var varCounter = 0
    private var funCounter = 0

    private var funtable = mutableListOf<MiniKotlinFunction>()
    private var symtable = mutableMapOf<String, MiniKotlinType>()

    fun generateProgram(maxFunctions: Int = 3): String {
        funCounter = 0
        funtable.clear()
        val functions = buildString {
            repeat(random.nextInt(maxFunctions)) {
                append(generateFunction())
            }
        }
        val main = generateFunction("main", MiniKotlinType.Unit, 0)
        // println("$functions$main")
        return "$functions$main"
    }

    private fun randomType(): MiniKotlinType =
        listOf(MiniKotlinType.Int, MiniKotlinType.String, MiniKotlinType.Boolean).random(random)

    private fun generateFunction(
        name: String = "f${funCounter++}", returnType: MiniKotlinType = randomType(), maxParams: Int = 3
    ): String {
        varCounter = 0
        symtable.clear()
        val parameters = if (maxParams > 0) {
            List(random.nextInt(maxParams)) { generateFunctionParameter() }
        } else {
            emptyList()
        }
        val paramsString = parameters.joinToString { "${it.name}: ${it.type.toKotlinString()}" }
        val returnExpression = if (returnType == MiniKotlinType.Unit) "" else generateExpression(returnType)
        val body = generateBlock()
        funtable.add(UserDefinedFunction(name, parameters, returnType, null))
        return "fun $name($paramsString): ${returnType.toKotlinString()} {\n$body\nreturn $returnExpression\n}\n\n"
    }

    private fun generateFunctionParameter(): MiniKotlinParam {
        val name = "v${varCounter++}"
        val type = randomType()
        symtable[name] = type
        return MiniKotlinParam(name, type)
    }

    private fun generateBlock(depth: Int = 0, maxStatements: Int = 10): String {
        if (depth > 3) return "" // Prevent infinite nesting

        val previousScope = symtable.toMutableMap()

        val statements = mutableListOf<String>()
        val numStatements = random.nextInt(1, maxStatements)

        repeat(numStatements) {
            val stmt = generateStatement(depth + 1)
            if (stmt.isNotBlank()) statements.add(stmt)
        }

        symtable = previousScope

        return statements.joinToString("\n") { it.prependIndent("    ") }
    }

    private fun generateStatement(depth: Int): String {
        val choices = buildList {
            add("VarDecl")
            if (symtable.isNotEmpty()) {
                add("Assignment")
                add("Print")
                add("Expression")
            }
            if (depth < 3) addAll(listOf("If", "While"))
        }

        return when (choices.random(random)) {
            "VarDecl" -> {
                val type = randomType()
                val name = "v${varCounter++}"
                val expr = generateExpression(type)
                symtable[name] = type
                "var $name: ${type.toKotlinString()} = $expr"
            }

            "Assignment" -> {
                val availableVars = symtable.keys.toList()
                val name = availableVars.random(random)
                val type = symtable[name]!!
                "$name = ${generateExpression(type)}"
            }

            "Print" -> {
                val availableVars = symtable.keys.toList()
                val name = availableVars.random(random)
                "println($name)"
            }

            "If" -> {
                val cond = generateExpression(MiniKotlinType.Boolean)
                val trueBlock = generateBlock(depth, 3)
                val falseBlock = generateBlock(depth, 3)
                "if ($cond) {\n$trueBlock\n} else {\n$falseBlock\n}"
            }

            "While" -> {
                // To prevent infinite loops during fuzzing, we use a loop counter
                val counterName = "loop$varCounter"
                varCounter++
                val block = generateBlock(depth, 3)
                val blockCode = if (block.isBlank()) "" else block.prependIndent("    ")
                val counterDeclaration = "var $counterName: Int = 0"
                val counterIncrement = "$counterName = $counterName + 1".prependIndent("    ")
                "$counterDeclaration\nwhile ($counterName < 3) {\n$counterIncrement\n$blockCode}"
            }

            "Expression" -> generateExpression(MiniKotlinType.Any, 3)

            else -> ""
        }
    }

    private fun generateExpression(type: MiniKotlinType, exprDepth: Int = 0): String {
        val varsOfType = symtable.entries.filter { it.value == type }.map { it.key }

        // 20% chance to use an existing variable if available
        if (varsOfType.isNotEmpty() && random.nextDouble() < 0.2) {
            return varsOfType.random(random)
        }

        val forceBaseCase = exprDepth >= 3
        val terminate = forceBaseCase || random.nextBoolean()

        val functionsOfType = funtable.filter { it.returnType == type }

        // 20% chance to use a pre-declared function
        if (!terminate && functionsOfType.isNotEmpty() && random.nextDouble() < 0.2) {
            val function = functionsOfType.random(random)
            val params = function.parameters.joinToString { generateExpression(it.type, 2) }
            return "${function.name}($params)"
        }

        return when (type) {
            MiniKotlinType.Int -> {
                if (terminate) {
                    random.nextInt(0, 100).toString()
                } else {
                    val op = listOf("+", "-", "*").random(random) // Omitted / and % to avoid divide-by-zero crashes
                    val lhs = generateExpression(MiniKotlinType.Int, exprDepth + 1)
                    val rhs = generateExpression(MiniKotlinType.Int, exprDepth + 1)
                    "($lhs $op $rhs)"
                }
            }

            MiniKotlinType.Boolean -> {
                if (terminate) {
                    random.nextBoolean().toString()
                } else if (random.nextBoolean()) {
                    val op = listOf("<", "<=", ">", ">=", "==", "!=").random(random)
                    val lhs = generateExpression(MiniKotlinType.Int, exprDepth + 1)
                    val rhs = generateExpression(MiniKotlinType.Int, exprDepth + 1)
                    "($lhs $op $rhs)"
                } else {
                    val op = listOf("&&", "||").random(random)
                    val lhs = generateExpression(MiniKotlinType.Boolean, exprDepth + 1)
                    val rhs = generateExpression(MiniKotlinType.Boolean, exprDepth + 1)
                    "($lhs $op $rhs)"
                }
            }

            MiniKotlinType.String -> {
                if (terminate || random.nextBoolean()) {
                    val stringLength = random.nextInt(1, 10)
                    // Generate printable ASCII (32..126)
                    // Exclude: '"' (34), '\' (92, breaks Java escapes), '$' (36, breaks Kotlin interpolation)
                    val safeChars = (32..126).filter { it != 34 && it != 92 && it != 36 }.map { it.toChar() }
                    val chars = (1..stringLength).map { safeChars.random(random) }.joinToString("")
                    "\"$chars\""
                } else {
                    val lhs = generateExpression(MiniKotlinType.String, exprDepth + 1)
                    val rhs = generateExpression(MiniKotlinType.String, 3)
                    "($lhs + $rhs)"
                }
            }

            MiniKotlinType.Unit -> "Unit"

            MiniKotlinType.Any -> generateExpression(randomType(), exprDepth)
        }
    }
}