package me.stageguard.obms.script.synthetic

import me.stageguard.obms.database.model.BeatmapSkillTable
import me.stageguard.obms.script.synthetic.wrapped.ColumnDeclaringBooleanWrapped
import org.ktorm.dsl.eq
import org.ktorm.dsl.or
import org.mozilla.javascript.NativeArray

object ConvenientToolsForBeatmapSkill {
    @JvmStatic
    fun contains(beatmapCollection: NativeArray) : ColumnDeclaringBooleanWrapped = kotlin.run {
        fun convert(int: Any?) = if(int == null) -1 else try {
            int.toString().toDouble().toInt().also { println(it) }
        } catch (ex: NumberFormatException) { -1 }

        ColumnDeclaringBooleanWrapped(
            if(beatmapCollection.isEmpty) {
                BeatmapSkillTable.bid.eq(-1)
            } else if(beatmapCollection.size == 1) {
                BeatmapSkillTable.bid.eq(convert(beatmapCollection.single()))
            } else {
                beatmapCollection.drop(1)
                    .map { BeatmapSkillTable.bid.eq(convert(it)) }
                    .fold(BeatmapSkillTable.bid.eq(convert(beatmapCollection.first()))) { r, t -> r.or(t) }
            }
        )
    }
}