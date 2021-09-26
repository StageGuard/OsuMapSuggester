package me.stageguard.obms.osu.api.oauth

enum class AuthType(val value: Int) {
    BIND_ACCOUNT(0),
    EDIT_RULESET(1),
    UNKNOWN(-1);

    companion object {
        fun getEnumByValue(v: Int) : AuthType {
            values().forEach {
                if (it.value == v) return it
            }
            return UNKNOWN
        }
    }
}