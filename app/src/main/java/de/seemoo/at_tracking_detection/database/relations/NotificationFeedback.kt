package de.seemoo.at_tracking_detection.database.relations

import androidx.room.Relation
import de.seemoo.at_tracking_detection.database.tables.Feedback
import java.time.LocalDateTime


data class NotificationFeedback(
    val notificationId: Int,
    val falseAlarm: Boolean,
    val createdAt: LocalDateTime,
    @Relation(
        parentColumn = "notificationId",
        entityColumn = "notificationId"
    )
    val feedback: Feedback?
)
