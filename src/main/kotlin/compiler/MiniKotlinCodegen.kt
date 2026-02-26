package compiler

class MiniKotlinCodegen {
    private val indentationLevel = 4
    private fun String.indent() = prependIndent(" ".repeat(indentationLevel))

    private var argCounter = 0
    private lateinit var currentReturnType: MiniKotlinType
    private var isMain = false

    fun generate(program: MiniKotlinAst.Program) =
        program.functionDeclaration.joinToString(separator = "\n\n") { generateFunctionDeclaration(it) }

    private fun generateFunctionDeclaration(functionDeclaration: MiniKotlinAst.FunctionDeclaration) =
        with(functionDeclaration) {
            currentReturnType = returnType
            argCounter = 0
            isMain = name == "main"

            val parameters = if (isMain) {
                "String[] args"
            } else {
                val parameters = parameterList.joinToString { "${it.type.generate()} ${it.name}" }
                val separator = if (parameters.isEmpty()) "" else ", "
                "$parameters${separator}Continuation<${returnType.generate()}> __continuation"
            }

            val next = if (returnType == MiniKotlinType.Unit && name != "main") "__continuation.accept(null);"
            else ""
            "public static void $name($parameters) ${generateBlock(block, next)}"
        }

    private fun generateBlock(block: MiniKotlinAst.Block, next: String): String =
        "{\n" + block.statements.foldRight(next) { statement, acc -> generateStatement(statement, acc) }
            .indent() + "\n}"

    private fun generateStatement(statement: MiniKotlinAst.Statement, next: String) = when (statement) {
        is MiniKotlinAst.VariableDeclaration -> generateVariableDeclaration(statement, next)
        is MiniKotlinAst.VariableAssignment -> generateVariableAssignment(statement, next)
        is MiniKotlinAst.If -> generateIf(statement, next)
        is MiniKotlinAst.While -> generateWhile(statement, next)
        is MiniKotlinAst.Expression -> generateExpression(statement) { next }
        is MiniKotlinAst.Return -> generateReturn(statement)
    }

    private fun generateVariableDeclaration(variableDeclaration: MiniKotlinAst.VariableDeclaration, next: String) =
        with(variableDeclaration) {
            generateExpression(value) { "${type.generate()}[] $javaName = new ${type.generate()}[] { $it };\n$next" }
        }

    private fun generateVariableAssignment(variableAssignment: MiniKotlinAst.VariableAssignment, next: String) =
        with(variableAssignment) {
            generateExpression(value) { "${identifier.javaName}[0] = $it;\n$next" }
        }

    private fun generateIf(ifStatement: MiniKotlinAst.If, next: String) = with(ifStatement) {
        val contName = "__cont$argCounter"
        val nextDecl = if (next.isNotEmpty()) "Continuation<Void> $contName = (__${argCounter++}) -> {\n${next.indent()}\n};\n" else ""
        val callNext = if (next.isNotEmpty()) "$contName.accept(null);" else ""

        val trueBlock = generateBlock(trueBlock, callNext)
        val falseBlock = falseBlock?.let {
            " else " + generateBlock(it, callNext)
        } ?: if (next.isEmpty()) "" else " else {\n${callNext.indent()}\n}"

        generateExpression(condition) { "${nextDecl}if ($it) $trueBlock$falseBlock" }
    }

    private fun generateWhile(whileStatement: MiniKotlinAst.While, next: String) = with(whileStatement) {
        val loopName = "__loop${argCounter}"
        val continuationDeclaration = "Continuation<Void>[] $loopName = new Continuation[1];"
        val loopAgain = "$loopName[0].accept(null);"
        val block = generateBlock(block, loopAgain)
        val arg = "__arg${argCounter++}"
        val continuationStart = "$loopName[0] = ($arg) ->"

        val falseBlock = if (next.isEmpty()) {
            "{}"
        } else {
            "{\n${next.indent()}\n}"
        }

        val loopBody = generateExpression(condition) { "if ($it) $block else $falseBlock" }.indent()

        "$continuationDeclaration\n$continuationStart {\n$loopBody\n};\n$loopAgain"
    }

    private fun generateReturn(returnStatement: MiniKotlinAst.Return) = with(returnStatement) {
        if (isMain) "" else value?.let { value -> generateExpression(value) { "__continuation.accept($it);\nreturn;" } }
            ?: "__continuation.accept(null);\nreturn;"
    }

    private fun generateExpression(expression: MiniKotlinAst.Expression, k: (String) -> String): String =
        when (expression) {
            is MiniKotlinAst.BinaryExpression -> generateBinaryExpression(expression, k)
            is MiniKotlinAst.BooleanLiteral -> k(expression.value.toString())
            is MiniKotlinAst.FunctionCall -> generateFunctionCall(expression, k)
            is MiniKotlinAst.Identifier -> k(expression.javaName + if (expression.isParam) "" else "[0]")
            is MiniKotlinAst.IntegerLiteral -> k(expression.value.toString())
            is MiniKotlinAst.Not -> generateExpression(expression.value) { k("!$it") }
            is MiniKotlinAst.StringLiteral -> k("\"${expression.value}\"")
        }

    private fun generateBinaryExpression(binaryExpression: MiniKotlinAst.BinaryExpression, k: (String) -> String) =
        with(binaryExpression) {
            when (operation) {
                MiniKotlinBinaryOperation.MULT, MiniKotlinBinaryOperation.DIV, MiniKotlinBinaryOperation.MOD, MiniKotlinBinaryOperation.PLUS, MiniKotlinBinaryOperation.MINUS, MiniKotlinBinaryOperation.LT, MiniKotlinBinaryOperation.GT, MiniKotlinBinaryOperation.LE, MiniKotlinBinaryOperation.GE -> {
                    generateExpression(left) { lValue ->
                        generateExpression(right) { rValue ->
                            k("($lValue $operation $rValue)")
                        }
                    }
                }

                MiniKotlinBinaryOperation.EQ -> generateExpression(left) { lValue ->
                    generateExpression(right) { rValue ->
                        k("java.util.Objects.equals($lValue, $rValue)")
                    }
                }

                MiniKotlinBinaryOperation.NEQ -> generateExpression(left) { lValue ->
                    generateExpression(right) { rValue ->
                        k("!java.util.Objects.equals($lValue, $rValue)")
                    }
                }

                MiniKotlinBinaryOperation.AND -> generateExpression(left) { lValue ->
                    "if ($lValue) {\n" + generateExpression(right) { rValue ->
                        k(rValue)
                    }.indent() + "\n} else {\n${k("false").indent()}\n}"
                }

                MiniKotlinBinaryOperation.OR -> generateExpression(left) { lValue ->
                    "if ($lValue) {\n${k("true").indent()}\n} else {\n" + generateExpression(right) { rValue ->
                        k(rValue)
                    }.indent() + "\n}"
                }
            }
        }

    private fun generateFunctionCall(functionCall: MiniKotlinAst.FunctionCall, k: (String) -> String): String =
        with(functionCall) {
            val tmpArg = "__arg${argCounter++}"
            val next = k(tmpArg).indent().trimEnd()
            val block = if (next.isEmpty()) "{});" else "{\n$next\n});"

            val evaluateArgs = argumentList.foldRight({ args: String ->
                "$name($args($tmpArg) -> $block"
            }) { expr, nextStep ->
                { currentArgs ->
                    generateExpression(expr) { nextStep("$currentArgs$it, ") }
                }
            }

            evaluateArgs("")
        }

    private fun MiniKotlinType.generate() = when (this) {
        MiniKotlinType.Int -> "Integer"
        MiniKotlinType.Boolean -> "Boolean"
        MiniKotlinType.String -> "String"
        MiniKotlinType.Any -> "Any"
        MiniKotlinType.Unit -> "Void"
    }
}