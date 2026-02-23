package compiler

enum class MiniKotlinBinaryOperation {
    MULT, DIV, MOD, PLUS, MINUS, LT, GT, LE, GE, EQ, NEQ, AND, OR;

    companion object {
        fun fromString(str: String) = when (str) {
            "*" -> MULT
            "/" -> DIV
            "%" -> MOD
            "+" -> PLUS
            "-" -> MINUS
            "<" -> LT
            ">" -> GT
            "<=" -> LE
            ">=" -> GE
            "==" -> EQ
            "!=" -> NEQ
            "&&" -> AND
            "||" -> OR
            else -> throw IllegalArgumentException("Unknown binary operation: $str")
        }
    }

    override fun toString(): String = when (this) {
        MULT -> "*"
        DIV -> "/"
        MOD -> "%"
        PLUS -> "+"
        MINUS -> "-"
        LT -> "<"
        GT -> ">"
        LE -> "<="
        GE -> ">="
        EQ -> "=="
        NEQ -> "!="
        AND -> "&&"
        OR -> "||"
    }
}