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
class MiniKotlinFuzzerGenerator(seed: Long = Random.nextLong(), private var budget: Int = 200) {
    private val random = Random(seed)
    private var varCounter = 0
    private var funCounter = 0

    private lateinit var currentFunction: MiniKotlinFunction

    private var funtable = mutableListOf<MiniKotlinFunction>()
    private var symtable = mutableMapOf<String, MiniKotlinType>()

    fun generateProgram(maxFunctions: Int = 5): String {
        funCounter = 0
        funtable.clear()
        val functions = buildString {
            repeat(random.nextInt(maxFunctions)) {
                append(generateFunction())
            }
        }
        val main = generateFunction("main", MiniKotlinType.Unit, 0)
        return "$functions$main"
    }

    private fun randomType(): MiniKotlinType =
        listOf(MiniKotlinType.Int, MiniKotlinType.String, MiniKotlinType.Boolean, MiniKotlinType.Unit).random(random)

    private fun randomNonUnitType(): MiniKotlinType =
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
        currentFunction = UserDefinedFunction(name, parameters, returnType, null)
        val returnExpression = if (returnType == MiniKotlinType.Unit) {
            if (random.nextBoolean()) "return"
            else ""
        } else "return " + generateExpression(returnType)
        val body = generateBlock()
        funtable.add(currentFunction)
        return "fun $name($paramsString): ${returnType.toKotlinString()} {\n$body\n${returnExpression.prependIndent("    ")}\n}\n\n"
    }

    private fun generateFunctionParameter(): MiniKotlinParam {
        val name = "v${varCounter++}"
        val type = randomNonUnitType()
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
        if (budget <= 0) return ""
        budget--

        val blockChance = 0.5 / (depth + 1) // Probability drops: 50% -> 25% -> 16%
        val choice = random.nextDouble()

        val assignableVars =
            symtable.keys.toList().filter { currentFunction.parameters.none { param -> param.name == it } }
        val choices = buildList {
            add("VarDecl")
            add("Print")
            add("Expression")
            if (funtable.any { it.returnType == MiniKotlinType.Unit }) {
                add("UnitFunctionCall")
            }
            if (assignableVars.isNotEmpty()) {
                add("Assignment")
            }
            if (choice < blockChance && depth < 3) addAll(listOf("If", "While"))
        }

        return when (choices.random(random)) {
            "VarDecl" -> {
                val type = randomNonUnitType()
                val name = "v${varCounter++}"
                val expr = generateExpression(type)
                symtable[name] = type
                "var $name: ${type.toKotlinString()} = $expr"
            }

            "Assignment" -> {
                val name = assignableVars.random(random)
                val type = symtable[name]!!
                "$name = ${generateExpression(type)}"
            }

            "Print" -> {
                val availableVars = symtable.keys.toList()
                val name = if (availableVars.isEmpty()) generateExpression(MiniKotlinType.Any)
                else availableVars.random(random)
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

            "Expression" -> generateExpression(randomType(), 3)

            "UnitFunctionCall" -> {
                val function = funtable.filter { it.returnType == MiniKotlinType.Unit }.random(random)
                val params = function.parameters.joinToString { generateExpression(it.type, 2) }
                "${function.name}($params)"
            }

            else -> ""
        }
    }

    private fun generateExpression(type: MiniKotlinType, exprDepth: Int = 0): String {
        budget--

        val varsOfType = symtable.entries.filter { it.value == type }.map { it.key }

        // 30% chance to use an existing variable if available
        if (varsOfType.isNotEmpty() && random.nextDouble() < 0.3) {
            return varsOfType.random(random)
        }

        val forceBaseCase = exprDepth >= 3
        val terminate = forceBaseCase || budget < 10 || random.nextBoolean()

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
                } else if (random.nextBoolean()) {
                    val op = listOf("==", "!=").random(random)
                    val lhs = generateExpression(MiniKotlinType.String, exprDepth + 1)
                    val rhs = generateExpression(MiniKotlinType.String, exprDepth + 1)
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
                    val rhs = generateExpression(MiniKotlinType.String, exprDepth + 1)
                    "($lhs + $rhs)"
                }
            }

            MiniKotlinType.Unit -> {
                if (!terminate && functionsOfType.isNotEmpty()) {
                    val function = functionsOfType.random(random)
                    val params = function.parameters.joinToString { generateExpression(it.type, 2) }
                    "${function.name}($params)"
                } else if (varsOfType.isNotEmpty()) {
                    varsOfType.random(random)
                } else {
                    "println(\"unit_fallback\")"
                }
            }

            MiniKotlinType.Any -> generateExpression(randomNonUnitType(), exprDepth)
        }
    }
}