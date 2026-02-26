package compiler

enum class MiniKotlinType {
    Int,
    Boolean,
    String,
    Any,
    Unit;

    override fun toString(): String = when (this) {
        Int -> "Int"
        Boolean -> "Boolean"
        String -> "String"
        Any -> "Any"
        Unit -> "Unit"
    }
}

infix fun MiniKotlinType.accepts(other: MiniKotlinType): Boolean {
    return this == MiniKotlinType.Any || other == MiniKotlinType.Any || this == other
}