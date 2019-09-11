@file:JvmName("NotificationHelper")

package com.zulipmobile.notifications

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.util.*

@JvmField
val TAG = "ZulipNotif"

/**
 * The Zulip messages we're showing as a notification, grouped by conversation.
 *
 * Each key identifies a conversation; see [buildKeyString].
 *
 * Each value is the messages in the conversation, in the order we
 * received them.
 *
 * When we start showing a separate notification for each  [Identity],
 * this type will represent the messages for just one [Identity].
 * See also [ConversationMap].
 */
open class ByConversationMap : LinkedHashMap<String, MutableList<MessageFcmMessage>>()

/**
 * All Zulip messages we're showing in notifications.
 *
 * Currently an alias of [ByConversationMap].  When we start showing
 * a separate notification for each [Identity], this type will become
 * a collection of one [ByConversationMap] per [Identity].
 */
class ConversationMap : ByConversationMap()

fun fetchBitmap(url: URL): Bitmap? {
    Log.i(TAG, "GAFT.fetch: Getting gravatar from url: $url")
    return try {
        val connection = url.openConnection()
        connection.useCaches = true
        (connection.content as? InputStream)
            ?.let { BitmapFactory.decodeStream(it) }
    } catch (e: IOException) {
        Log.e(TAG, "ERROR: $e")
        null
    }
}

fun sizedURL(context: Context, url: URL, dpSize: Float): URL {
    // From http://stackoverflow.com/questions/4605527/
    val r = context.resources
    val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        dpSize, r.displayMetrics)
    val query = if (url.query != null) "${url.query}&s=$px" else "s=$px"
    return URL(url, "?$query")
}

fun buildNotificationContent(conversations: ByConversationMap, inboxStyle: Notification.InboxStyle, mContext: Context) {
    for (messages in conversations.values) {

        val messagesByNameMap = buildMessagesByNameMap(messages)
        val builder = SpannableStringBuilder()

        for (name in messagesByNameMap.keys) {
            builder.append("$name, ")
        }
        val namesLength = builder.length
        builder.replace(namesLength - 2, namesLength - 1, ":")
        builder.append(messages[messages.size - 1].content)
        builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, namesLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        inboxStyle.addLine(builder)
    }
}

fun buildMessagesByNameMap(allMessages: List<MessageFcmMessage>): LinkedHashMap<String, MutableList<MessageFcmMessage>> {
    val map = LinkedHashMap<String, MutableList<MessageFcmMessage>>()
    for (message in allMessages) {
        val name = message.sender.fullName
        var messagesForName: MutableList<MessageFcmMessage>? = map[name]
        if (messagesForName == null) {
            messagesForName = ArrayList()
        }
        messagesForName.add(message)
        map[name] = messagesForName
    }
    return map
}

fun extractTotalMessagesCount(conversations: ByConversationMap): Int {
    var totalNumber = 0
    for ((_, value) in conversations) {
        totalNumber += value.size
    }
    return totalNumber
}

/**
 * Formats -
 * stream message - fullName:streamName:'stream'
 * group message - fullName:Recipients:'group'
 * private message - fullName:Email:'private'
 */
private fun buildKeyString(fcmMessage: MessageFcmMessage): String {
    val recipient = fcmMessage.recipient
    return when (recipient) {
        is Recipient.Stream -> String.format("%s:stream", recipient.stream)
        is Recipient.GroupPm -> String.format("%s:group", recipient.getPmUsersString())
        is Recipient.Pm -> String.format("%s:private", fcmMessage.sender.email)
    }
}

fun extractNames(conversations: ByConversationMap): ArrayList<String> {
    val namesSet = LinkedHashSet<String>()
    for (fcmMessages in conversations.values) {
        for (fcmMessage in fcmMessages) {
            namesSet.add(fcmMessage.sender.fullName)
        }
    }
    return ArrayList(namesSet)
}

fun addConversationToMap(fcmMessage: MessageFcmMessage, conversations: ConversationMap) {
    val key = buildKeyString(fcmMessage)
    var messages: MutableList<MessageFcmMessage>? = conversations[key]
    if (messages == null) {
        messages = ArrayList()
    }
    messages.add(fcmMessage)
    conversations[key] = messages
}

fun removeMessagesFromMap(conversations: ConversationMap, removeFcmMessage: RemoveFcmMessage) {
    // We don't have the information to compute what key we ought to find each message under,
    // so just walk the whole thing.  If the user has >100 notifications, this linear scan
    // won't be their worst problem anyway...
    //
    // TODO redesign this whole data structure, for many reasons.
    val it = conversations.values.iterator()
    while (it.hasNext()) {
        val messages: MutableList<MessageFcmMessage> = it.next()
        for (i in messages.indices.reversed()) {
            if (removeFcmMessage.messageIds.contains(messages[i].zulipMessageId)) {
                messages.removeAt(i)
            }
        }
        if (messages.isEmpty()) {
            it.remove()
        }
    }
}

fun clearConversations(conversations: ConversationMap) {
    conversations.clear()
}