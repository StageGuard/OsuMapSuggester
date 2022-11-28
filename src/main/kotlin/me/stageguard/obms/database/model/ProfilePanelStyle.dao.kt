package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import org.ktorm.dsl.*
import org.ktorm.entity.Entity
import org.ktorm.schema.*

object ProfilePanelStyleTable : AddableTable<ProfilePanelStyle>("profile_panel_style") {
    val id = int("id").primaryKey().bindTo { it.id }
    val qq = long("qq").bindTo { it.qq }
    val type = int("type").bindTo { it.type }
    val blurRadius = double("blurRadius").bindTo { it.blurRadius }
    val backgroundAlpha = double("backgroundAlpha").bindTo { it.backgroundAlpha }
    val cardBackgroundAlpha = double("cardBackgroundAlpha").bindTo { it.cardBackgroundAlpha }
    val useCustomBG = boolean("useCustomBG").bindTo { it.useCustomBg }

    override fun <T : AssignmentsBuilder> T.mapElement(element: ProfilePanelStyle) {
        set(qq, element.qq)
        set(type, element.type)
        set(blurRadius, element.blurRadius)
        set(backgroundAlpha, element.backgroundAlpha)
        set(cardBackgroundAlpha, element.cardBackgroundAlpha)
        set(useCustomBG, element.useCustomBg)
    }
}

interface ProfilePanelStyle : Entity<ProfilePanelStyle> {
    companion object : Entity.Factory<ProfilePanelStyle>()
    var id: Int
    var qq: Long
    var type: Int
    var blurRadius: Double
    var backgroundAlpha: Double
    var cardBackgroundAlpha: Double
    var useCustomBg: Boolean
}