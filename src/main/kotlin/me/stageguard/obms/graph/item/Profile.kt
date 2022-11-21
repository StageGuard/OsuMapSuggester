package me.stageguard.obms.graph.item

import io.github.humbleui.skija.Surface
import me.stageguard.obms.osu.api.dto.GetUserDTO

object Profile {
    fun drawProfilePanel(profile: GetUserDTO): Surface {

        return Surface.makeRasterN32Premul(1, 1)
    }
}
