package me.stageguard.obms.utils.bmf

enum class BMFCharset(val value: Int) {
    ANSI(0),
    Default(1),
    Symbol(2),
    Mac(77),
    ShiftJIS(128),
    Hangul(129),
    Johab(130),
    GB2312(134),
    ChineseBig5(136),
    Greek(161),
    Turkish(162),
    Vietnamese(163),
    Hebrew(177),
    Arabic(178),
    Baltic(186),
    Russian(204),
    Thai(222),
    EastEurope(238),
    OEM(255),
}