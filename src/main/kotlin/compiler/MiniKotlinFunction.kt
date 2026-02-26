package compiler

import MiniKotlinParser

sealed interface MiniKotlinFunction {
    val name: String
    val parameters: List<MiniKotlinParam>
    val returnType: MiniKotlinType
}

data class UserDefinedFunction(
    override val name: String,
    override val parameters: List<MiniKotlinParam>,
    override val returnType: MiniKotlinType,
    val blockCtx: MiniKotlinParser.BlockContext?
) : MiniKotlinFunction {
    lateinit var block: String

    override fun toString(): String {
        val additionalParameter = if (name != "main") {
            "Continuation<$returnType> __continuation"
        } else {
            "String[] args"
        }
        val separator = if (parameters.isNotEmpty()) ", " else ""
        val parameterList = "(${parameters.joinToString()}$separator$additionalParameter)"
        val header = "public static void $name$parameterList"
        return "$header $block\n\n"
    }
}

data class BuiltinFunction(
    override val name: String,
    override val parameters: List<MiniKotlinParam>,
    override val returnType: MiniKotlinType,
) : MiniKotlinFunction

val defaultBuiltins = mapOf(
    "println" to BuiltinFunction(
        name = "Prelude.println",
        parameters = listOf(MiniKotlinParam("value", MiniKotlinType.Any)),
        returnType = MiniKotlinType.Unit,
    )
)
