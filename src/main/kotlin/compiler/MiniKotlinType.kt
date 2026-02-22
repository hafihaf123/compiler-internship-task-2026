package org.example.compiler

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
}