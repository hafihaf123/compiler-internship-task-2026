package compiler

data class MiniKotlinParam(val name: String, val type: MiniKotlinType) {
    override fun toString(): String = "${type.generate()} $name"

    private fun MiniKotlinType.generate() = when (this) {
        MiniKotlinType.Int -> "Integer"
        MiniKotlinType.Boolean -> "Boolean"
        MiniKotlinType.String -> "String"
        MiniKotlinType.Any -> "Any"
        MiniKotlinType.Unit -> "Void"
    }
}
