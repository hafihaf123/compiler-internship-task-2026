package compiler

import MiniKotlinBaseVisitor
import MiniKotlinParser

class MiniKotlinCompiler : MiniKotlinBaseVisitor<String>() {
    fun compile(program: MiniKotlinParser.ProgramContext, className: String = "MiniProgram"): String {
        val ast = MiniKotlinParserVisitor().visitProgram(program)
        MiniKotlinSemanticAnalyser(ast).analyse()
        val code = MiniKotlinCodegen().generate(ast)
        return "public class $className {\n${code.prependIndent(" ".repeat(4))}\n}"
    }
}
