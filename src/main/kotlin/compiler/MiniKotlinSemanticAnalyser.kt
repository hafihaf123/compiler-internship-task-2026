package compiler

class MiniKotlinSemanticAnalyser(var program: MiniKotlinAst.Program) {
    data class VariableSymbol(val type: MiniKotlinType, val javaName: String, val isParam: Boolean)

    sealed interface FunctionSymbol {
        val name: String
        val parameterList: List<MiniKotlinAst.Parameter>
        val returnType: MiniKotlinType
    }

    data class UserDefinedFunction(
        override val name: String,
        override val parameterList: List<MiniKotlinAst.Parameter>,
        override val returnType: MiniKotlinType,
        val block: MiniKotlinAst.Block
    ) : FunctionSymbol

    data class BuiltinFunction(
        override val name: String,
        override val parameterList: List<MiniKotlinAst.Parameter>,
        override val returnType: MiniKotlinType,
    ) : FunctionSymbol

    private val symtable = ArrayDeque<MutableMap<String, VariableSymbol>>()
    private val functionTable = mutableMapOf<String, FunctionSymbol>()
    private var currentReturnType: MiniKotlinType = MiniKotlinType.Unit
    private var varCounter = 0

    init {
        functionTable["println"] = BuiltinFunction(
            "Prelude.println", listOf(
                MiniKotlinAst.Parameter(
                    "value", MiniKotlinType.Any
                )
            ), MiniKotlinType.Unit
        )
        program.functionDeclaration.forEach { (name, parameterList, returnType, block) ->
            functionTable[name] = UserDefinedFunction(name, parameterList, returnType, block)
        }
    }

    private fun lookupVariable(name: String): VariableSymbol? = symtable.firstNotNullOfOrNull { it[name] }

    fun analyse() = program.functionDeclaration.forEach(::analyseFunctionDeclaration)

    private fun analyseFunctionDeclaration(functionDeclaration: MiniKotlinAst.FunctionDeclaration) =
        with(functionDeclaration) {
            if (name == "main" && returnType != MiniKotlinType.Unit) error("'main' function should always return Unit")
            if (returnType != MiniKotlinType.Unit && !alwaysReturns(block)) error("Missing return statement in function '$name'")

            currentReturnType = returnType
            symtable.addFirst(mutableMapOf())
            parameterList.forEach(::analyseParameter)
            analyseBlock(block)
            symtable.removeFirst()
        }

    private fun analyseParameter(parameter: MiniKotlinAst.Parameter) = with(parameter) {
        symtable.first()[name] = VariableSymbol(type, name, true)
    }

    private fun analyseBlock(block: MiniKotlinAst.Block) {
        symtable.addFirst(mutableMapOf())
        block.statements.forEach(::analyseStatement)
        symtable.removeFirst()
    }

    private fun analyseStatement(statement: MiniKotlinAst.Statement) {
        when (statement) {
            is MiniKotlinAst.Expression -> analyseExpression(statement)
            is MiniKotlinAst.If -> analyseIf(statement)
            is MiniKotlinAst.Return -> analyseReturn(statement)
            is MiniKotlinAst.VariableAssignment -> analyseVariableAssignment(statement)
            is MiniKotlinAst.VariableDeclaration -> analyseVariableDeclaration(statement)
            is MiniKotlinAst.While -> analyseWhile(statement)
        }
    }

    private fun analyseReturn(statement: MiniKotlinAst.Return) {
        statement.value?.let { value ->
            analyseExpression(value)
            if (value.resolvedType != currentReturnType) error("Return type mismatch: expected ${currentReturnType}, got ${value.resolvedType!!}")
        }
            ?: if (currentReturnType != MiniKotlinType.Unit) error("Return type mismatch: expected ${currentReturnType}, got Unit") else {
            }
    }

    private fun analyseIf(ifStatement: MiniKotlinAst.If) = with(ifStatement) {
        analyseExpression(condition)
        if (condition.resolvedType != MiniKotlinType.Boolean) error("If condition type mismatch: Expected 'Boolean', got '${condition.resolvedType}'")
        analyseBlock(trueBlock)
        falseBlock?.let(::analyseBlock)
    }

    private fun analyseVariableAssignment(variableAssignment: MiniKotlinAst.VariableAssignment) =
        with(variableAssignment) {
            analyseIdentifier(identifier)
            if (identifier.isParam) error("Cannot assign value to parameter ${identifier.name}")

            analyseExpression(value)
            if (identifier.resolvedType != value.resolvedType) error("Type mismatch in assignment to variable ${identifier.name}")
        }

    private fun analyseVariableDeclaration(variableDeclaration: MiniKotlinAst.VariableDeclaration) =
        with(variableDeclaration) {
            if (name in symtable.first()) error("Variable redeclaration in the same scope of variable '$name'")
            analyseExpression(value)
            if (value.resolvedType != type) error("Type mismatch in variable declaration of variable $name")
            javaName = "${name}_${varCounter++}"
            symtable.first()[name] = VariableSymbol(type, javaName, false)
        }

    private fun analyseWhile(whileStatement: MiniKotlinAst.While) = with(whileStatement) {
        analyseExpression(condition)
        if (condition.resolvedType != MiniKotlinType.Boolean) error("While condition type mismatch: Expected 'Boolean', got '${condition.resolvedType}'")
        analyseBlock(block)
    }

    private fun analyseExpression(expression: MiniKotlinAst.Expression) {
        when (expression) {
            is MiniKotlinAst.FunctionCall -> analyseFunctionCall(expression)
            is MiniKotlinAst.Identifier -> analyseIdentifier(expression)
            is MiniKotlinAst.BinaryExpression -> analyseBinaryExpression(expression)
            is MiniKotlinAst.Not -> analyseNot(expression)
            is MiniKotlinAst.IntegerLiteral -> expression.resolvedType = MiniKotlinType.Int
            is MiniKotlinAst.BooleanLiteral -> expression.resolvedType = MiniKotlinType.Boolean
            is MiniKotlinAst.StringLiteral -> expression.resolvedType = MiniKotlinType.String
        }
    }

    private fun analyseNot(expression: MiniKotlinAst.Not) = with(expression) {
        analyseExpression(value)
        if (value.resolvedType != MiniKotlinType.Boolean) error("Invalid type for not expression: ${value.resolvedType}")
        expression.resolvedType = MiniKotlinType.Boolean
    }

    private fun analyseFunctionCall(functionCall: MiniKotlinAst.FunctionCall) {
        val function = functionTable[functionCall.name] ?: error("Called function not found: ${functionCall.name}")
        functionCall.name = function.name
        for ((arg, param) in functionCall.argumentList.zip(function.parameterList)) {
            analyseExpression(arg)
            if (arg.resolvedType?.accepts(param.type)?.not() ?: false) {
                error("Wrong parameter type for parameter '${param.name}' in function '${function.name}'")
            }
        }
        functionCall.resolvedType = function.returnType
    }

    private fun analyseIdentifier(identifier: MiniKotlinAst.Identifier) = with(identifier) {
        val variable = lookupVariable(name) ?: error("Variable not found: $name")
        resolvedType = variable.type
        javaName = variable.javaName
        isParam = variable.isParam
    }

    private fun analyseBinaryExpression(binaryExpression: MiniKotlinAst.BinaryExpression) {
        analyseExpression(binaryExpression.left)
        analyseExpression(binaryExpression.right)
        val leftType = binaryExpression.left.resolvedType
        val rightType = binaryExpression.right.resolvedType

        binaryExpression.resolvedType = when (binaryExpression.operation) {
            MiniKotlinBinaryOperation.MULT, MiniKotlinBinaryOperation.DIV, MiniKotlinBinaryOperation.MOD, MiniKotlinBinaryOperation.MINUS -> {
                if (leftType == MiniKotlinType.Int && rightType == MiniKotlinType.Int) MiniKotlinType.Int
                else null
            }

            MiniKotlinBinaryOperation.PLUS -> when (leftType) {
                MiniKotlinType.Int if rightType == MiniKotlinType.Int -> MiniKotlinType.Int
                MiniKotlinType.String -> MiniKotlinType.String
                else -> null
            }

            MiniKotlinBinaryOperation.LT, MiniKotlinBinaryOperation.GT, MiniKotlinBinaryOperation.LE, MiniKotlinBinaryOperation.GE -> {
                if (leftType == MiniKotlinType.Int && rightType == MiniKotlinType.Int) MiniKotlinType.Boolean
                else null
            }

            MiniKotlinBinaryOperation.EQ, MiniKotlinBinaryOperation.NEQ -> {
                if (leftType == rightType) MiniKotlinType.Boolean
                else null
            }

            MiniKotlinBinaryOperation.AND, MiniKotlinBinaryOperation.OR -> {
                if (leftType == MiniKotlinType.Boolean && rightType == MiniKotlinType.Boolean) MiniKotlinType.Boolean
                else null
            }
        } ?: error("Invalid types for binary expression: $leftType ${binaryExpression.operation} $rightType")
    }

    // Definite return analysis helpers

    private fun alwaysReturns(block: MiniKotlinAst.Block): Boolean = block.statements.any(::alwaysReturns)

    private fun alwaysReturns(statement: MiniKotlinAst.Statement) = with(statement) {
        when (this) {
            is MiniKotlinAst.If -> alwaysReturns(trueBlock) && falseBlock?.let(::alwaysReturns) ?: false
            is MiniKotlinAst.Return -> true
            is MiniKotlinAst.Expression, is MiniKotlinAst.VariableAssignment, is MiniKotlinAst.VariableDeclaration, is MiniKotlinAst.While -> false
        }
    }
}