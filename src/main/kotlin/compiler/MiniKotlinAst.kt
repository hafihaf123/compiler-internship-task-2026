package compiler

class MiniKotlinAst {
    sealed interface AstNode

    data class Program(val functionDeclaration: List<FunctionDeclaration>) : AstNode

    data class FunctionDeclaration(
        val name: String, val parameterList: List<Parameter>, val returnType: MiniKotlinType, val block: Block
    ) : AstNode

    data class Parameter(val name: String, val type: MiniKotlinType) : AstNode

    data class Block(val statements: List<Statement>) : AstNode

    sealed interface Statement : AstNode

    sealed class Expression : Statement {
        var resolvedType: MiniKotlinType? = null
    }

    data class VariableDeclaration(
        val name: String, val type: MiniKotlinType, val value: Expression, var javaName: String = ""
    ) : Statement

    data class VariableAssignment(
        val identifier: Identifier, val value: Expression
    ) : Statement

    data class If(val condition: Expression, val trueBlock: Block, val falseBlock: Block?) : Statement

    data class While(val condition: Expression, val block: Block) : Statement

    data class Return(val value: Expression?) : Statement

    data class FunctionCall(var name: String, val argumentList: List<Expression>) : Expression()

    data class Not(val value: Expression) : Expression()

    data class BinaryExpression(
        val left: Expression, val operation: MiniKotlinBinaryOperation, val right: Expression
    ) : Expression()

    data class IntegerLiteral(val value: Int) : Expression()

    data class StringLiteral(val value: String) : Expression()

    data class BooleanLiteral(val value: Boolean) : Expression()

    data class Identifier(val name: String, var javaName: String = "", var isParam: Boolean = false) :
        Expression()
}