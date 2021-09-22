package me.stageguard.obms.script.synthetic.wrapped

import kotlinx.coroutines.runBlocking
import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.database.model.BeatmapTypeTable
import org.ktorm.dsl.eq
import org.ktorm.dsl.or
import org.mozilla.javascript.NativeArray

object ConvenientToolsForBeatmapSkill {
    @JvmStatic
    fun contains(beatmapCollection: NativeArray) : ColumnDeclaringBooleanWrapped = kotlin.run {
        fun convert(int: Any?) = if(int == null) -1 else try {
            int.toString().toDouble().toInt()
        } catch (ex: NumberFormatException) { -1 }

        val filtered = beatmapCollection.map { convert(it) }.filter { it != -1 }

        runBlocking {
            BeatmapSkillTable.addAllViaBid(filtered, true)
        }

        ColumnDeclaringBooleanWrapped(
            if(filtered.isEmpty()) {
                BeatmapSkillTable.bid.eq(-1)
            } else if(filtered.size == 1) {
                BeatmapSkillTable.bid.eq(filtered.single())
            } else {
                filtered.drop(1)
                    .map { BeatmapSkillTable.bid.eq(it) }
                    .fold(BeatmapSkillTable.bid.eq(filtered.first())) { r, t -> r.or(t) }
            }
        )
    }
}