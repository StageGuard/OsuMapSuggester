package me.stageguard.obms.osu.processor.replay

enum class Key {
    None,
    M1,
    M2,
    K1,
    K2,
    Smoke;

    companion object {
        fun parse(v: Int) : List<Key> = mutableListOf<Key>().also {
            if(v == 0) {
                it.add(None)
                return@also
            }
            when {
                (1 shl 0 and v) > 0 -> it.add(M1)
                (1 shl 1 and v) > 0 -> it.add(M2)
                (1 shl 2 and v) > 0 -> it.add(K1)
                (1 shl 3 and v) > 0 -> it.add(K2)
                (1 shl 4 and v) > 0 -> it.add(Smoke)
            }
        }
    }
}