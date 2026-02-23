package compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser
import compiler.MiniKotlinBinaryOperation.*

private const val INDENT_COUNT = 4

private fun String.indent() = prependIndent(" ".repeat(INDENT_COUNT))

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {
    private val functionTable = mutableMapOf<String, MiniKotlinFunction>().apply { putAll(defaultBuiltins) }

    // the first scope is the top one, the last one is the global scope
    private val symtable = ArrayDeque<MutableMap<String, MiniKotlinType>>()

    private fun lookupType(name: String): MiniKotlinType? = symtable.firstNotNullOfOrNull { it[name] }

    private val variableInfo = mutableMapOf<String, Boolean>()

    private var currentReturnType: MiniKotlinType = MiniKotlinType.Unit

    private var tmpCounter = 0
        get() = field++

    fun compile(program: MiniKotlinParser.ProgramContext, className: String = "MiniProgram"): String =
        "public class $className {\n${visitProgram(program).trimEnd().indent()}\n}"

    override fun visitProgram(ctx: MiniKotlinParser.ProgramContext): String {
        ctx.functionDeclaration().forEach {
            val function = parseFunctionDeclaration(it)
            functionTable[function.name] = function
        }
        val builder = StringBuilder()
        for (function in functionTable.values) {
            if (function !is UserDefinedFunction) continue
            currentReturnType = function.returnType
            parseFunctionDeclaration(function)
            builder.append(function.toString())
        }
        return builder.toString()
    }

    private fun parseFunctionDeclaration(ctx: MiniKotlinParser.FunctionDeclarationContext): UserDefinedFunction {
        val name = ctx.IDENTIFIER().text
        val parameters = parseParameterList(ctx.parameterList())
        val returnType = parseType(ctx.type())
        if (name == "main" && returnType != MiniKotlinType.Unit)
            error("Main function shouldn't return any value")
        val blockCtx = ctx.block()
        return UserDefinedFunction(name, parameters, returnType, blockCtx)
    }

    private fun parseFunctionDeclaration(function: UserDefinedFunction) {
        symtable.addFirst(mutableMapOf())
        function.parameters.forEach { symtable.first()[it.name] = it.type }
        checkBlock(function.blockCtx!!)
        function.block = parseBlock(function.blockCtx, "")
        symtable.removeFirst()
    }

    private fun parseParameterList(ctx: MiniKotlinParser.ParameterListContext?): List<MiniKotlinParam> =
        ctx?.parameter()?.map { parseParameter(it) } ?: emptyList()

    private fun parseParameter(ctx: MiniKotlinParser.ParameterContext): MiniKotlinParam {
        val name = ctx.IDENTIFIER().text
        variableInfo[name] =  false
        return MiniKotlinParam(name, parseType(ctx.type()))
    }

    private fun parseType(ctx: MiniKotlinParser.TypeContext): MiniKotlinType = when {
        ctx.INT_TYPE() != null -> MiniKotlinType.Int
        ctx.BOOLEAN_TYPE() != null -> MiniKotlinType.Boolean
        ctx.STRING_TYPE() != null -> MiniKotlinType.String
        ctx.UNIT_TYPE() != null -> MiniKotlinType.Unit
        else -> throw IllegalArgumentException()
    }

    private fun parseBlock(ctx: MiniKotlinParser.BlockContext, next: String): String {
        val statements = ctx.statement()
        val result = statements.foldRight(next) { statement, acc ->
            parseStatement(statement, acc)
        }.trimEnd()
        return if (result.isEmpty()) "{}" else "{\n${result.indent()}\n}"
    }

    private fun checkBlock(ctx: MiniKotlinParser.BlockContext) {
        symtable.addFirst(mutableMapOf())
        for (statement in ctx.statement()) {
            checkStatement(statement)
        }
        symtable.removeFirst()
    }

    private fun parseStatement(ctx: MiniKotlinParser.StatementContext, next: String): String = when {
        ctx.variableDeclaration() != null -> parseVariableDeclaration(ctx.variableDeclaration(), next)
        ctx.ifStatement() != null -> parseIfStatement(ctx.ifStatement(), next)
        ctx.whileStatement() != null -> parseWhileStatement(ctx.whileStatement(), next)
        ctx.variableAssignment() != null -> parseVariableAssignment(ctx.variableAssignment(), next)
        ctx.returnStatement() != null -> visitReturnStatement(ctx.returnStatement())
        ctx.expression() != null -> parseExpression(ctx.expression()) { next }
        else -> error("Unknown StatementContext type: ${ctx.javaClass.simpleName}")
    }

    private fun checkStatement(ctx: MiniKotlinParser.StatementContext) = when {
        ctx.variableDeclaration() != null -> checkVariableDeclaration(ctx.variableDeclaration())
        ctx.ifStatement() != null -> checkIfStatement(ctx.ifStatement())
        ctx.whileStatement() != null -> checkWhileStatement(ctx.whileStatement())
        ctx.variableAssignment() != null -> checkVariableAssignment(ctx.variableAssignment())
        ctx.returnStatement() != null -> checkReturnStatement(ctx.returnStatement())
        ctx.expression() != null -> checkExpression(ctx.expression())
        else -> error("Unknown StatementContext type: ${ctx.javaClass.simpleName}")
    }

    private fun parseVariableDeclaration(ctx: MiniKotlinParser.VariableDeclarationContext, next: String): String {
        val type = parseType(ctx.type())
        val name = ctx.IDENTIFIER().text
        return parseExpression(ctx.expression()) { "$type[] $name = new $type[] { $it };\n$next" }
    }

    private fun checkVariableDeclaration(ctx: MiniKotlinParser.VariableDeclarationContext) {
        val type = parseType(ctx.type())
        val valueType = checkExpression(ctx.expression())
        if (valueType != type) error("The Lvalue and Rvalue in variable declaration have different types")
        val name = ctx.IDENTIFIER().text
        variableInfo[name] = true
        symtable.first()[name] = type
    }

    private fun parseVariableAssignment(ctx: MiniKotlinParser.VariableAssignmentContext, next: String): String {
        val name = ctx.IDENTIFIER().text
        return parseExpression(ctx.expression()) { "$name[0] = $it;\n$next" }
    }

    private fun checkVariableAssignment(ctx: MiniKotlinParser.VariableAssignmentContext) {
        val name = ctx.IDENTIFIER().text
        val variableType = lookupType(name) ?: error("Assignment to undeclared variable")
        val valueType = checkExpression(ctx.expression())
        if (variableType != valueType) error("The Lvalue and Rvalue in variable assignment have different types")
    }

    private fun parseIfStatement(ctx: MiniKotlinParser.IfStatementContext, next: String): String {
        val trueBlock = parseBlock(ctx.block(0), next)
        val falseBlock = if (ctx.ELSE() != null) {
            " else " + parseBlock(ctx.block(1), next)
        } else {
            if (next.isEmpty()) {
                ""
            } else {
                " else {\n${next.indent()}\n}"
            }
        }

        return parseExpression(ctx.expression()) { "if ($it) $trueBlock$falseBlock" }
    }

    private fun checkIfStatement(ctx: MiniKotlinParser.IfStatementContext) {
        checkBlock(ctx.block(0))
        if (ctx.ELSE() != null) {
            checkBlock(ctx.block(1))
        }
        val valueType = checkExpression(ctx.expression())
        if (valueType != MiniKotlinType.Boolean) error("Non-Boolean condition in if statement")
    }

    private fun parseWhileStatement(ctx: MiniKotlinParser.WhileStatementContext, next: String): String {
        val loopName = "__loop$tmpCounter"
        val continuationDeclaration = "Continuation<Void>[] $loopName = new Continuation[1];"
        val loopAgain = "$loopName[0].accept(null);"
        val block = parseBlock(ctx.block(), loopAgain)
        val arg = "__arg$tmpCounter"
        val continuationStart = "$loopName[0] = ($arg) ->"
        val next = next.trimEnd()

        val falseBlock = if (next.isEmpty()) {
            "{}"
        } else {
            "{\n${next.indent()}\n}"
        }

        val loopBody = parseExpression(ctx.expression()) { "if ($it) $block else $falseBlock" }.indent()

        return "$continuationDeclaration\n$continuationStart {\n$loopBody\n};\n$loopAgain"
    }

    private fun checkWhileStatement(ctx: MiniKotlinParser.WhileStatementContext) {
        checkBlock(ctx.block())

        val valueType = checkExpression(ctx.expression())
        if (valueType != MiniKotlinType.Boolean) error("Non-Boolean condition in while statement")
    }

    override fun visitReturnStatement(ctx: MiniKotlinParser.ReturnStatementContext): String {
        val expression = ctx.expression() ?: run {
            if (currentReturnType == MiniKotlinType.Unit) return ""
            return "__continuation.accept(null);\nreturn;"
        }
        return parseExpression(expression) { "__continuation.accept($it);\nreturn;" }
    }

    private fun checkReturnStatement(ctx: MiniKotlinParser.ReturnStatementContext) {
        val expression = ctx.expression() ?: run {
            if (currentReturnType != MiniKotlinType.Unit) error("Expected $currentReturnType but returned Unit")
            return
        }
        val valueType = checkExpression(expression)
        if (valueType != currentReturnType) error("Function return type does not match with returned value")
    }

    private fun parseExpression(
        ctx: MiniKotlinParser.ExpressionContext, k: (value: String) -> String
    ): String = when (ctx) {
        is MiniKotlinParser.PrimaryExprContext -> parsePrimaryExpression(ctx.primary(), k)

        is MiniKotlinParser.NotExprContext -> parseExpression(ctx.expression()) { k("!$it") }

        is MiniKotlinParser.MulDivExprContext, is MiniKotlinParser.AddSubExprContext, is MiniKotlinParser.ComparisonExprContext, is MiniKotlinParser.EqualityExprContext, is MiniKotlinParser.AndExprContext, is MiniKotlinParser.OrExprContext -> parseBinary(
            ctx.getChild(0) as MiniKotlinParser.ExpressionContext,
            MiniKotlinBinaryOperation.fromString(ctx.getChild(1).text),
            ctx.getChild(2) as MiniKotlinParser.ExpressionContext,
            k
        )

        is MiniKotlinParser.FunctionCallExprContext -> {
            val name = ctx.IDENTIFIER().text
            val functionName = functionTable[name]!!.name
            parseArgumentList(ctx.argumentList().expression()) { args ->
                val tmpArg = "__arg$tmpCounter"
                val next = k(tmpArg).indent().trimEnd()
                val block = if (next.isEmpty()) "{});" else "{\n$next\n});"
                "${functionName}(${args.joinToString(", ")}, ($tmpArg) -> $block"
            }
        }

        else -> throw UnsupportedOperationException("Expression type not implemented: ${ctx.javaClass.simpleName}")
    }

    private fun checkExpression(
        ctx: MiniKotlinParser.ExpressionContext
    ): MiniKotlinType = when (ctx) {
        is MiniKotlinParser.PrimaryExprContext -> checkPrimaryExpression(ctx.primary())

        is MiniKotlinParser.NotExprContext -> {
            val valueType = checkExpression(ctx.expression())
            if (valueType != MiniKotlinType.Boolean) error("Negation operator on a non-Boolean expression")
            valueType
        }

        is MiniKotlinParser.MulDivExprContext, is MiniKotlinParser.AddSubExprContext, is MiniKotlinParser.ComparisonExprContext, is MiniKotlinParser.EqualityExprContext, is MiniKotlinParser.AndExprContext, is MiniKotlinParser.OrExprContext -> {
            checkBinary(
                ctx.getChild(0) as MiniKotlinParser.ExpressionContext,
                MiniKotlinBinaryOperation.fromString(ctx.getChild(1).text),
                ctx.getChild(2) as MiniKotlinParser.ExpressionContext,
            )
        }

        is MiniKotlinParser.FunctionCallExprContext -> {
            val name = ctx.IDENTIFIER().text
            val function = functionTable[name] ?: error("Called function not found: $name")
            checkArgumentList(ctx.argumentList().expression(), function.parameters)
            function.returnType
        }

        else -> throw UnsupportedOperationException("Expression type not implemented: ${ctx.javaClass.simpleName}")
    }

    private fun parseBinary(
        left: MiniKotlinParser.ExpressionContext,
        op: MiniKotlinBinaryOperation,
        right: MiniKotlinParser.ExpressionContext,
        k: (String) -> String
    ): String {
        return parseExpression(left) { lValue ->
            parseExpression(right) { rValue ->
                k("($lValue $op $rValue)")
            }
        }
    }

    private fun checkBinary(
        left: MiniKotlinParser.ExpressionContext,
        op: MiniKotlinBinaryOperation,
        right: MiniKotlinParser.ExpressionContext,
    ): MiniKotlinType {
        val lValueType = checkExpression(left)
        val rValueType = checkExpression(right)
        if (lValueType != rValueType) error("Binary operations with different types not supported")
        return when (op) {
            LT, GT, LE, GE, EQ, NEQ, AND, OR -> MiniKotlinType.Boolean
            else -> lValueType
        }
    }

    private fun parseArgumentList(
        args: List<MiniKotlinParser.ExpressionContext>, onDone: (List<String>) -> String
    ): String {
        if (args.isEmpty()) return onDone(emptyList())

        fun resolveNext(index: Int, acc: List<String>): String {
            if (index == args.size) return onDone(acc)

            return parseExpression(args[index]) {
                resolveNext(index + 1, acc + it)
            }
        }

        return resolveNext(0, emptyList())
    }

    private fun checkArgumentList(args: List<MiniKotlinParser.ExpressionContext>, parameters: List<MiniKotlinParam>) {
        args.zip(parameters).forEach { (arg, parameter) ->
            val valueType = checkExpression(arg)
            if (parameter.type != MiniKotlinType.Any && valueType != parameter.type) error("Parameter types not matching in function call")
        }
    }

    private fun parsePrimaryExpression(
        ctx: MiniKotlinParser.PrimaryContext, k: (String) -> String
    ): String = when (ctx) {
        is MiniKotlinParser.IntLiteralContext, is MiniKotlinParser.StringLiteralContext, is MiniKotlinParser.BoolLiteralContext -> {
            k(ctx.getChild(0).text)
        }

        is MiniKotlinParser.IdentifierExprContext -> {
            val name = ctx.IDENTIFIER().text
            val suffix = if (variableInfo[name] ?: false) "[0]" else ""
            k("$name$suffix")
        }

        is MiniKotlinParser.ParenExprContext -> parseExpression(ctx.expression(), k)

        else -> error("Unknown PrimaryContext type: ${ctx.javaClass.simpleName}")
    }

    private fun checkPrimaryExpression(
        ctx: MiniKotlinParser.PrimaryContext
    ): MiniKotlinType = when (ctx) {
        is MiniKotlinParser.IntLiteralContext -> MiniKotlinType.Int

        is MiniKotlinParser.StringLiteralContext -> MiniKotlinType.String

        is MiniKotlinParser.BoolLiteralContext -> MiniKotlinType.Boolean

        is MiniKotlinParser.ParenExprContext -> checkExpression(ctx.expression())

        is MiniKotlinParser.IdentifierExprContext -> {
            val varName = ctx.IDENTIFIER().text
            lookupType(varName) ?: error("Variable not found: $varName")
        }

        else -> error("Unknown PrimaryContext type: ${ctx.javaClass.simpleName}")
    }
}
