package me.stageguard.obms.osu.api.oauth

sealed class BindResult(
    val qq: Long,
    val groupBind: Long,
    val osuId: Int,
    val osuName: String
) {
    class BindSuccessful(
        qq: Long,
        groupBind: Long,
        osuId: Int,
        osuName: String
    ) : BindResult(qq, groupBind, osuId, osuName)
    class ChangeBinding(
        qq: Long,
        groupBind: Long,
        osuId: Int,
        osuName: String,
        val oldOsuId: Int,
        val oldOsuName: String
    ) : BindResult(qq, groupBind, osuId, osuName)
    class AlreadyBound(
        qq: Long,
        groupBind: Long,
        osuId: Int,
        osuName: String
    ) : BindResult(qq, groupBind, osuId, osuName)
}
