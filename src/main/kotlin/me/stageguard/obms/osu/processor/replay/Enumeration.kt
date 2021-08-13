package me.stageguard.obms.osu.processor.replay

enum class Key {
    None,
    M1,
    M2,
    K1,
    K2;

    companion object {
        fun parse(v: Int) : List<Key> = when(v) {
            0 -> listOf(None)
            1 -> listOf(M1)
            2 -> listOf(M2)
            3 -> listOf(M1, M2)
            5 -> listOf(K1)
            10 -> listOf(K2)
            15 -> listOf(K1, K2)
            else -> throw IllegalArgumentException("Unknown key: $v")
        }
    }
}