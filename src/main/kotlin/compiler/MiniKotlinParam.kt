package compiler

data class MiniKotlinParam(val name: String, val type: MiniKotlinType) {
    override fun toString(): String = "$type $name"
}
