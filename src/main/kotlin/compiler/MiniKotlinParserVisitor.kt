package compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinParserVisitor : MiniKotlinBaseVisitor<MiniKotlinAst.AstNode>() {
    private fun parseType(ctx: MiniKotlinParser.TypeContext): MiniKotlinType = when {
        ctx.INT_TYPE() != null -> MiniKotlinType.Int
        ctx.BOOLEAN_TYPE() != null -> MiniKotlinType.Boolean
        ctx.STRING_TYPE() != null -> MiniKotlinType.String
        ctx.UNIT_TYPE() != null -> MiniKotlinType.Unit
        else -> throw IllegalArgumentException()
    }

    override fun visitProgram(ctx: MiniKotlinParser.ProgramContext) =
        MiniKotlinAst.Program(ctx.functionDeclaration().map(::visitFunctionDeclaration))

    override fun visitFunctionDeclaration(ctx: MiniKotlinParser.FunctionDeclarationContext) =
        MiniKotlinAst.FunctionDeclaration(
            ctx.IDENTIFIER().text,
            ctx.parameterList()?.parameter()?.map(::visitParameter).orEmpty(),
            parseType(ctx.type()),
            visitBlock(ctx.block())
        )

    override fun visitParameter(ctx: MiniKotlinParser.ParameterContext): MiniKotlinAst.Parameter =
        MiniKotlinAst.Parameter(ctx.IDENTIFIER().text, parseType(ctx.type()))

    override fun visitBlock(ctx: MiniKotlinParser.BlockContext) =
        MiniKotlinAst.Block(ctx.statement().map(::visitStatement))

    override fun visitStatement(ctx: MiniKotlinParser.StatementContext): MiniKotlinAst.Statement = when {
        ctx.variableDeclaration() != null -> visitVariableDeclaration(ctx.variableDeclaration())
        ctx.ifStatement() != null -> visitIfStatement(ctx.ifStatement())
        ctx.whileStatement() != null -> visitWhileStatement(ctx.whileStatement())
        ctx.variableAssignment() != null -> visitVariableAssignment(ctx.variableAssignment())
        ctx.returnStatement() != null -> visitReturnStatement(ctx.returnStatement())
        ctx.expression() != null -> visitExpression(ctx.expression())
        else -> throw UnsupportedOperationException("Expression type not implemented: ${ctx.javaClass.simpleName}")
    }

    private fun visitExpression(ctx: MiniKotlinParser.ExpressionContext): MiniKotlinAst.Expression = when (ctx) {
        is MiniKotlinParser.FunctionCallExprContext -> visitFunctionCallExpr(ctx)
        is MiniKotlinParser.PrimaryExprContext -> visitPrimary(ctx.primary())
        is MiniKotlinParser.NotExprContext -> visitNotExpr(ctx)
        is MiniKotlinParser.MulDivExprContext, is MiniKotlinParser.AddSubExprContext, is MiniKotlinParser.ComparisonExprContext, is MiniKotlinParser.EqualityExprContext, is MiniKotlinParser.AndExprContext, is MiniKotlinParser.OrExprContext -> MiniKotlinAst.BinaryExpression(
            visitExpression(ctx.getChild(0) as MiniKotlinParser.ExpressionContext),
            MiniKotlinBinaryOperation.fromString(ctx.getChild(1).text),
            visitExpression(ctx.getChild(2) as MiniKotlinParser.ExpressionContext)
        )

        else -> throw UnsupportedOperationException("Expression type not implemented: ${ctx.javaClass.simpleName}")
    }

    override fun visitVariableDeclaration(ctx: MiniKotlinParser.VariableDeclarationContext) =
        MiniKotlinAst.VariableDeclaration(
            ctx.IDENTIFIER().text, parseType(ctx.type()), visitExpression(ctx.expression())
        )

    override fun visitVariableAssignment(ctx: MiniKotlinParser.VariableAssignmentContext) =
        MiniKotlinAst.VariableAssignment(
            MiniKotlinAst.Identifier(ctx.IDENTIFIER().text), visitExpression(ctx.expression())
        )

    override fun visitIfStatement(ctx: MiniKotlinParser.IfStatementContext) = MiniKotlinAst.If(
        visitExpression(ctx.expression()),
        visitBlock(ctx.block()[0]!!),
        ctx.ELSE()?.let { visitBlock(ctx.block()[1]!!) })

    override fun visitWhileStatement(ctx: MiniKotlinParser.WhileStatementContext) =
        MiniKotlinAst.While(visitExpression(ctx.expression()), visitBlock(ctx.block()))

    override fun visitReturnStatement(ctx: MiniKotlinParser.ReturnStatementContext) =
        MiniKotlinAst.Return(ctx.expression()?.let(::visitExpression))

    override fun visitFunctionCallExpr(ctx: MiniKotlinParser.FunctionCallExprContext) = MiniKotlinAst.FunctionCall(
        ctx.IDENTIFIER().text, ctx.argumentList()?.expression()?.map(::visitExpression).orEmpty()
    )

    override fun visitNotExpr(ctx: MiniKotlinParser.NotExprContext) =
        MiniKotlinAst.Not(visitExpression(ctx.expression()))

    private fun visitPrimary(ctx: MiniKotlinParser.PrimaryContext): MiniKotlinAst.Expression = when (ctx) {
        is MiniKotlinParser.IntLiteralContext -> MiniKotlinAst.IntegerLiteral(ctx.text.toInt())
        is MiniKotlinParser.StringLiteralContext -> MiniKotlinAst.StringLiteral(ctx.text.trim('"'))
        is MiniKotlinParser.BoolLiteralContext -> MiniKotlinAst.BooleanLiteral(ctx.text.toBooleanStrict())
        is MiniKotlinParser.IdentifierExprContext -> MiniKotlinAst.Identifier(ctx.text)
        is MiniKotlinParser.ParenExprContext -> visitExpression(ctx.expression())
        else -> error("Unknown PrimaryContext type: ${ctx.javaClass.simpleName}")
    }
}