package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import me.stageguard.obms.database.model.BeatmapSkillTable.bindTo
import me.stageguard.obms.database.model.RulesetCollection.bindTo
import me.stageguard.obms.database.model.RulesetCollection.primaryKey
import org.ktorm.dsl.AssignmentsBuilder
import org.ktorm.entity.Entity
import org.ktorm.schema.int
import org.ktorm.schema.long
import org.ktorm.schema.varchar

object BeatmapCommentTable : AddableTable<BeatmapComment>("beatmap_comment") {
    val id = int("id").primaryKey().bindTo { it.id }
    val bid = int("bid").bindTo { it.bid }
    val rulesetId = int("rulesetId").bindTo { it.rulesetId }
    val commenterQq = long("commenterQq").bindTo { it.commenterQq }
    val content = varchar("content").bindTo { it.content }

    override fun <T : AssignmentsBuilder> T.mapElement(element: BeatmapComment) {
        set(id, element.id)
        set(bid, element.bid)
        set(rulesetId, element.rulesetId)
        set(commenterQq, element.commenterQq)
        set(content, element.content)
    }
}

interface BeatmapComment : Entity<BeatmapComment> {
    companion object : Entity.Factory<BeatmapComment>()
    var id: Int
    var bid: Int
    var rulesetId: Int
    var commenterQq: Long
    var content: String
}