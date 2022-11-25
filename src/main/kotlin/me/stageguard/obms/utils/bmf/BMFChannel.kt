package me.stageguard.obms.utils.bmf

enum class BMFChannel(val value: Int) {
    None(0),
    Blue(1),
    Green(2),
    Red(4),
    Alpha(8),
    All(15),
}