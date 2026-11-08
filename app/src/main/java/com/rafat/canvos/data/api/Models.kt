package com.rafat.canvos.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Canvas REST API models. Every field is optional with a default: Canvas omits or
 * nulls many fields depending on permissions, course settings and "include[]".
 */

/** Accepts both `123` and `"course_123"`. */
object FlexibleIdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleId", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        return if (el is JsonPrimitive) el.content else el.toString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@Serializable
data class User(
    val id: Long = 0,
    val name: String = "",
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("sortable_name") val sortableName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("login_id") val loginId: String? = null,
    @SerialName("primary_email") val primaryEmail: String? = null,
    val email: String? = null,
    val pronouns: String? = null,
    @SerialName("time_zone") val timeZone: String? = null,
    val enrollments: List<Enrollment> = emptyList(),
    /** Only on /users/self/profile: the user's iCal feed. */
    val calendar: CalendarFeed? = null,
)

@Serializable
data class CalendarFeed(val ics: String? = null)

@Serializable
data class Term(
    val id: Long? = null,
    val name: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
)

@Serializable
data class Enrollment(
    val id: Long? = null,
    val type: String? = null,
    val role: String? = null,
    @SerialName("enrollment_state") val enrollmentState: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("course_id") val courseId: Long? = null,
    @SerialName("computed_current_score") val currentScore: Double? = null,
    @SerialName("computed_current_grade") val currentGrade: String? = null,
    @SerialName("computed_final_score") val finalScore: Double? = null,
    @SerialName("computed_final_grade") val finalGrade: String? = null,
    val grades: Grades? = null,
)

@Serializable
data class Grades(
    @SerialName("current_score") val currentScore: Double? = null,
    @SerialName("current_grade") val currentGrade: String? = null,
    @SerialName("final_score") val finalScore: Double? = null,
    @SerialName("final_grade") val finalGrade: String? = null,
)

@Serializable
data class Course(
    val id: Long = 0,
    val name: String = "",
    @SerialName("course_code") val courseCode: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val term: Term? = null,
    val enrollments: List<Enrollment> = emptyList(),
    @SerialName("image_download_url") val imageUrl: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("default_view") val defaultView: String? = null,
    @SerialName("syllabus_body") val syllabusBody: String? = null,
    @SerialName("workflow_state") val workflowState: String? = null,
    @SerialName("access_restricted_by_date") val accessRestricted: Boolean = false,
    @SerialName("hide_final_grades") val hideFinalGrades: Boolean = false,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    val concluded: Boolean? = null,
)

/** /api/v1/dashboard/dashboard_cards uses camelCase. */
@Serializable
data class DashboardCard(
    val id: Long = 0,
    val shortName: String? = null,
    val originalName: String? = null,
    val courseCode: String? = null,
    val term: String? = null,
    val image: String? = null,
    val color: String? = null,
    val position: Int? = null,
    val isFavorited: Boolean = false,
    val enrollmentType: String? = null,
    val defaultView: String? = null,
)

@Serializable
data class Tab(
    val id: String = "",
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("full_url") val fullUrl: String? = null,
    val label: String = "",
    val type: String? = null,
    val hidden: Boolean? = null,
    val visibility: String? = null,
    val position: Int = 0,
)

@Serializable
data class CustomColors(@SerialName("custom_colors") val colors: Map<String, String> = emptyMap())

@Serializable
data class CourseNickname(
    @SerialName("course_id") val courseId: Long = 0,
    val name: String? = null,
    val nickname: String? = null,
)

@Serializable
data class LockInfo(
    @SerialName("asset_string") val assetString: String? = null,
    @SerialName("unlock_at") val unlockAt: String? = null,
    @SerialName("lock_at") val lockAt: String? = null,
)

@Serializable
data class AssignmentGroup(
    val id: Long = 0,
    val name: String = "",
    val position: Int = 0,
    @SerialName("group_weight") val groupWeight: Double? = null,
    val assignments: List<Assignment> = emptyList(),
)

@Serializable
data class RubricRating(
    val id: String? = null,
    val points: Double? = null,
    val description: String? = null,
    @SerialName("long_description") val longDescription: String? = null,
)

@Serializable
data class RubricCriterion(
    val id: String = "",
    val points: Double? = null,
    val description: String? = null,
    @SerialName("long_description") val longDescription: String? = null,
    val ratings: List<RubricRating> = emptyList(),
)

@Serializable
data class RubricAssessmentEntry(
    val points: Double? = null,
    @SerialName("rating_id") val ratingId: String? = null,
    val comments: String? = null,
)

@Serializable
data class ExternalToolAttrs(val url: String? = null, @SerialName("new_tab") val newTab: Boolean? = null)

@Serializable
data class DiscussionRef(val id: Long = 0)

@Serializable
data class Assignment(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("lock_at") val lockAt: String? = null,
    @SerialName("unlock_at") val unlockAt: String? = null,
    @SerialName("points_possible") val pointsPossible: Double? = null,
    @SerialName("grading_type") val gradingType: String? = null,
    @SerialName("submission_types") val submissionTypes: List<String> = emptyList(),
    @SerialName("allowed_extensions") val allowedExtensions: List<String> = emptyList(),
    @SerialName("allowed_attempts") val allowedAttempts: Int? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("course_id") val courseId: Long = 0,
    @SerialName("assignment_group_id") val assignmentGroupId: Long? = null,
    val submission: Submission? = null,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
    @SerialName("lock_info") val lockInfo: LockInfo? = null,
    @SerialName("quiz_id") val quizId: Long? = null,
    @SerialName("is_quiz_assignment") val isQuizAssignment: Boolean = false,
    @SerialName("discussion_topic") val discussionTopic: DiscussionRef? = null,
    @SerialName("external_tool_tag_attributes") val externalTool: ExternalToolAttrs? = null,
    @SerialName("omit_from_final_grade") val omitFromFinalGrade: Boolean = false,
    val published: Boolean = true,
    val rubric: List<RubricCriterion> = emptyList(),
    @SerialName("has_submitted_submissions") val hasSubmittedSubmissions: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MediaComment(
    @SerialName("display_name") val displayName: String? = null,
    val url: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
data class CommentAuthor(
    val id: Long? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_image_url") val avatarImageUrl: String? = null,
)

@Serializable
data class SubmissionComment(
    val id: Long = 0,
    @SerialName("author_id") val authorId: Long? = null,
    @SerialName("author_name") val authorName: String? = null,
    val author: CommentAuthor? = null,
    val comment: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val attachments: List<CanvasFile> = emptyList(),
    @SerialName("media_comment") val mediaComment: MediaComment? = null,
)

@Serializable
data class Submission(
    val id: Long = 0,
    @SerialName("assignment_id") val assignmentId: Long? = null,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("workflow_state") val workflowState: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("graded_at") val gradedAt: String? = null,
    val score: Double? = null,
    val grade: String? = null,
    @SerialName("entered_grade") val enteredGrade: String? = null,
    @SerialName("points_deducted") val pointsDeducted: Double? = null,
    val late: Boolean = false,
    val missing: Boolean = false,
    val excused: Boolean? = null,
    val attempt: Int? = null,
    @SerialName("submission_type") val submissionType: String? = null,
    val body: String? = null,
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    val attachments: List<CanvasFile> = emptyList(),
    @SerialName("submission_comments") val comments: List<SubmissionComment> = emptyList(),
    @SerialName("rubric_assessment") val rubricAssessment: Map<String, RubricAssessmentEntry>? = null,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("submission_history") val history: List<Submission> = emptyList(),
) {
    val isSubmitted: Boolean
        get() = submittedAt != null || workflowState == "submitted" || workflowState == "pending_review" ||
            (workflowState == "graded" && submissionType != null)
    val isGraded: Boolean get() = workflowState == "graded" && (grade != null || score != null) || excused == true
}

@Serializable
data class CanvasFile(
    val id: Long = 0,
    @SerialName("display_name") val displayName: String? = null,
    val filename: String? = null,
    @SerialName("content-type") val contentType: String? = null,
    val url: String? = null,
    val size: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("mime_class") val mimeClass: String? = null,
    val locked: Boolean = false,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
    val hidden: Boolean = false,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("folder_id") val folderId: Long? = null,
) {
    val name: String get() = displayName ?: filename ?: "file"
}

@Serializable
data class Folder(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("files_count") val filesCount: Int = 0,
    @SerialName("folders_count") val foldersCount: Int = 0,
    @SerialName("parent_folder_id") val parentFolderId: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("hidden_for_user") val hiddenForUser: Boolean = false,
)

@Serializable
data class CompletionRequirement(
    val type: String? = null,
    @SerialName("min_score") val minScore: Double? = null,
    val completed: Boolean? = null,
)

@Serializable
data class ContentDetails(
    @SerialName("points_possible") val pointsPossible: Double? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("unlock_at") val unlockAt: String? = null,
    @SerialName("lock_at") val lockAt: String? = null,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
)

@Serializable
data class ModuleItem(
    val id: Long = 0,
    @SerialName("module_id") val moduleId: Long = 0,
    val position: Int = 0,
    val title: String = "",
    val indent: Int = 0,
    val type: String = "",
    @SerialName("content_id") val contentId: Long? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val url: String? = null,
    @SerialName("page_url") val pageUrl: String? = null,
    @SerialName("external_url") val externalUrl: String? = null,
    @SerialName("new_tab") val newTab: Boolean? = null,
    @SerialName("completion_requirement") val completionRequirement: CompletionRequirement? = null,
    @SerialName("content_details") val contentDetails: ContentDetails? = null,
    val published: Boolean? = null,
)

@Serializable
data class Module(
    val id: Long = 0,
    val name: String = "",
    val position: Int = 0,
    @SerialName("unlock_at") val unlockAt: String? = null,
    @SerialName("require_sequential_progress") val requireSequentialProgress: Boolean? = null,
    val state: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("items_count") val itemsCount: Int = 0,
    val items: List<ModuleItem>? = null,
)

@Serializable
data class Page(
    @SerialName("page_id") val pageId: Long? = null,
    val url: String = "",
    val title: String = "",
    val body: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("front_page") val frontPage: Boolean = false,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class DiscussionAuthor(
    val id: Long? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_image_url") val avatarImageUrl: String? = null,
    val pronouns: String? = null,
)

@Serializable
data class TopicPermissions(val reply: Boolean = false, val attach: Boolean = false)

@Serializable
data class DiscussionTopic(
    val id: Long = 0,
    val title: String? = null,
    val message: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("delayed_post_at") val delayedPostAt: String? = null,
    @SerialName("last_reply_at") val lastReplyAt: String? = null,
    val author: DiscussionAuthor? = null,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("discussion_subentry_count") val replyCount: Int = 0,
    @SerialName("read_state") val readState: String? = null,
    @SerialName("unread_count") val unreadCount: Int = 0,
    val locked: Boolean = false,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
    val pinned: Boolean = false,
    val attachments: List<CanvasFile> = emptyList(),
    @SerialName("assignment_id") val assignmentId: Long? = null,
    @SerialName("context_code") val contextCode: String? = null,
    @SerialName("require_initial_post") val requireInitialPost: Boolean? = null,
    @SerialName("user_can_see_posts") val userCanSeePosts: Boolean = true,
    val permissions: TopicPermissions? = null,
    @SerialName("discussion_type") val discussionType: String? = null,
)

@Serializable
data class DiscussionParticipant(
    val id: Long = 0,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_image_url") val avatarImageUrl: String? = null,
)

@Serializable
data class DiscussionEntry(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
    val replies: List<DiscussionEntry> = emptyList(),
    val attachment: CanvasFile? = null,
    val attachments: List<CanvasFile> = emptyList(),
)

@Serializable
data class DiscussionView(
    val participants: List<DiscussionParticipant> = emptyList(),
    @SerialName("unread_entries") val unreadEntries: List<Long> = emptyList(),
    val view: List<DiscussionEntry> = emptyList(),
    @SerialName("new_entries") val newEntries: List<DiscussionEntry> = emptyList(),
)

@Serializable
data class ConversationParticipant(
    val id: Long = 0,
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val pronouns: String? = null,
)

@Serializable
data class ConversationMessage(
    val id: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val body: String? = null,
    @SerialName("author_id") val authorId: Long? = null,
    val generated: Boolean = false,
    val attachments: List<CanvasFile> = emptyList(),
    @SerialName("media_comment") val mediaComment: MediaComment? = null,
    @SerialName("forwarded_messages") val forwardedMessages: List<ConversationMessage> = emptyList(),
    @SerialName("participating_user_ids") val participatingUserIds: List<Long> = emptyList(),
)

@Serializable
data class Conversation(
    val id: Long = 0,
    val subject: String? = null,
    @SerialName("workflow_state") val workflowState: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_authored_message_at") val lastAuthoredMessageAt: String? = null,
    @SerialName("message_count") val messageCount: Int = 0,
    val starred: Boolean = false,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val participants: List<ConversationParticipant> = emptyList(),
    val audience: List<Long> = emptyList(),
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("context_code") val contextCode: String? = null,
    val messages: List<ConversationMessage> = emptyList(),
    @SerialName("cannot_reply") val cannotReply: Boolean? = null,
)

@Serializable
data class Recipient(
    @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
    val name: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val type: String? = null,
    @SerialName("user_count") val userCount: Int? = null,
)

@Serializable
data class PlannerOverride(
    val id: Long = 0,
    @SerialName("plannable_type") val plannableType: String? = null,
    @SerialName("plannable_id") val plannableId: Long? = null,
    @SerialName("marked_complete") val markedComplete: Boolean = false,
    val dismissed: Boolean = false,
)

@Serializable
data class Plannable(
    val id: Long = 0,
    val title: String? = null,
    val name: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("todo_date") val todoDate: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("all_day") val allDay: Boolean? = null,
    @SerialName("points_possible") val pointsPossible: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
    val details: String? = null,
)

@Serializable
data class PlannerItem(
    @SerialName("context_type") val contextType: String? = null,
    @SerialName("course_id") val courseId: Long? = null,
    @SerialName("plannable_id") val plannableId: Long = 0,
    @SerialName("plannable_type") val plannableType: String = "",
    @SerialName("plannable_date") val plannableDate: String? = null,
    val plannable: Plannable = Plannable(),
    /** `false`, or an object with submitted/graded/missing/late flags. */
    val submissions: JsonElement? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("planner_override") val plannerOverride: PlannerOverride? = null,
    @SerialName("new_activity") val newActivity: Boolean = false,
) {
    private fun flag(name: String): Boolean {
        val s = submissions as? JsonObject ?: return false
        val v = s[name] ?: return false
        return v != JsonNull && v.jsonPrimitive.booleanOrNull == true
    }

    val title: String get() = plannable.title ?: plannable.name ?: "Untitled"
    val isSubmitted: Boolean get() = flag("submitted")
    val isGraded: Boolean get() = flag("graded")
    val isMissing: Boolean get() = flag("missing")
    val isLate: Boolean get() = flag("late")
    val isExcused: Boolean get() = flag("excused")
    val isComplete: Boolean
        get() = plannerOverride?.markedComplete ?: (isSubmitted || isGraded || isExcused)
}

@Serializable
data class CalendarEvent(
    @Serializable(with = FlexibleIdSerializer::class) val id: String = "",
    val title: String? = null,
    @SerialName("start_at") val startAt: String? = null,
    @SerialName("end_at") val endAt: String? = null,
    @SerialName("all_day") val allDay: Boolean = false,
    val description: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("location_address") val locationAddress: String? = null,
    @SerialName("context_code") val contextCode: String? = null,
    @SerialName("context_name") val contextName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val type: String? = null,
)

@Serializable
data class StreamSubmissionAssignment(val name: String? = null, @SerialName("points_possible") val pointsPossible: Double? = null)

@Serializable
data class StreamItem(
    val id: Long = 0,
    val title: String? = null,
    val message: String? = null,
    val type: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("context_type") val contextType: String? = null,
    @SerialName("course_id") val courseId: Long? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("read_state") val readState: Boolean? = null,
    @SerialName("discussion_topic_id") val discussionTopicId: Long? = null,
    @SerialName("announcement_id") val announcementId: Long? = null,
    @SerialName("conversation_id") val conversationId: Long? = null,
    @SerialName("assignment_id") val assignmentId: Long? = null,
    val assignment: StreamSubmissionAssignment? = null,
    val score: Double? = null,
    val grade: String? = null,
    @SerialName("notification_category") val notificationCategory: String? = null,
    @SerialName("submission_comments") val submissionComments: List<SubmissionComment> = emptyList(),
)

@Serializable
data class Quiz(
    val id: Long = 0,
    val title: String = "",
    @SerialName("html_url") val htmlUrl: String? = null,
    val description: String? = null,
    @SerialName("quiz_type") val quizType: String? = null,
    @SerialName("time_limit") val timeLimit: Int? = null,
    @SerialName("allowed_attempts") val allowedAttempts: Int? = null,
    @SerialName("points_possible") val pointsPossible: Double? = null,
    @SerialName("question_count") val questionCount: Int? = null,
    @SerialName("due_at") val dueAt: String? = null,
    @SerialName("lock_at") val lockAt: String? = null,
    @SerialName("unlock_at") val unlockAt: String? = null,
    @SerialName("locked_for_user") val lockedForUser: Boolean = false,
    @SerialName("lock_explanation") val lockExplanation: String? = null,
    val published: Boolean? = null,
)

@Serializable
data class SessionToken(@SerialName("session_url") val sessionUrl: String = "")

@Serializable
data class UploadTicket(
    @SerialName("upload_url") val uploadUrl: String = "",
    @SerialName("upload_params") val uploadParams: Map<String, JsonElement> = emptyMap(),
    @SerialName("file_param") val fileParam: String? = null,
)

internal fun JsonElement.asParamString(): String =
    (this as? JsonPrimitive)?.contentOrNull ?: toString()
