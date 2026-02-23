package compiler

enum class MiniKotlinType {
    Int,
    Boolean,
    String,
    Any,
    Unit;

    override fun toString(): String = when (this) {
        Int -> "Integer"
        Boolean -> "Boolean"
        String -> "String"
        Any -> "Any"
        Unit -> "Void"
    }

    fun toKotlinString(): String = when (this) {
        Int -> "Int"
        Boolean -> "Boolean"
        String -> "String"
        Any -> "Any"
        Unit -> "Unit"
    }
}