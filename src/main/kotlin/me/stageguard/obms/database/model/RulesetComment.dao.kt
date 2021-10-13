package me.stageguard.obms.database.model

import me.stageguard.obms.database.AddableTable
import org.ktorm.dsl.AssignmentsBuilder
import org.ktorm.entity.Entity
import org.ktorm.schema.int
import org.ktorm.schema.long

object RulesetCommentTable : AddableTable<RulesetComment>("ruleset_user_comment") {
    val id = int("id").primaryKey().bindTo { it.id }
    val rulesetId = int("rulesetId").bindTo { it.rulesetId }
    val commenterQq = long("commenterQq").bindTo { it.commenterQq }
    val positive = int("positive").bindTo { it.positive }

    override fun <T : AssignmentsBuilder> T.mapElement(element: RulesetComment) {
        set(id, element.id)
        set(rulesetId, element.rulesetId)
        set(commenterQq, element.commenterQq)
        set(positive, element.positive)
    }
}

interface RulesetComment : Entity<RulesetComment> {
    companion object : Entity.Factory<RulesetComment>()
    var id: Int
    var rulesetId: Int
    var commenterQq: Long
    var positive: Int
}