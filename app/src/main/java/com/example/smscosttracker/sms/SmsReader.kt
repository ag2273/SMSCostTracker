package com.example.smscosttracker.sms

import android.content.Context
import android.provider.Telephony
import com.example.smscosttracker.data.RawSms

data class SmsThread(
    val threadId: Long,
    val address: String,
    val messageCount: Int,
    val snippet: String,
    val lastDate: Long
)

object SmsReader {
    fun readThreads(context: Context): List<SmsThread> {
        val latest = LinkedHashMap<Long, SmsThread>()
        val counts = HashMap<Long, Int>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val threadColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadColumn)
                counts.merge(threadId, 1, Int::plus)
                if (threadId in latest) continue
                latest[threadId] = SmsThread(
                    threadId = threadId,
                    address = cursor.getString(addressColumn) ?: "",
                    messageCount = 0,
                    snippet = cursor.getString(bodyColumn) ?: "",
                    lastDate = cursor.getLong(dateColumn)
                )
            }
        }
        return latest.values.map { it.copy(messageCount = counts[it.threadId] ?: 0) }
    }

    /** Messages from [threadIds] newer than [sinceMillis]; other threads are never read. */
    fun readMessagesSince(context: Context, threadIds: Set<Long>, sinceMillis: Long): List<RawSms> {
        if (threadIds.isEmpty()) return emptyList()
        val messages = mutableListOf<RawSms>()
        val placeholders = threadIds.joinToString(",") { "?" }
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.THREAD_ID
            ),
            "${Telephony.Sms.THREAD_ID} IN ($placeholders) AND ${Telephony.Sms.DATE} > ?",
            (threadIds.map { it.toString() } + sinceMillis.toString()).toTypedArray(),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val threadColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (cursor.moveToNext()) {
                messages += RawSms(
                    address = cursor.getString(addressColumn) ?: "",
                    body = cursor.getString(bodyColumn) ?: "",
                    date = cursor.getLong(dateColumn),
                    threadId = cursor.getLong(threadColumn)
                )
            }
        }
        return messages
    }

    fun readMessages(context: Context, threadId: Long): List<RawSms> {
        val messages = mutableListOf<RawSms>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.THREAD_ID
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val threadColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            while (cursor.moveToNext()) {
                messages += RawSms(
                    address = cursor.getString(addressColumn) ?: "",
                    body = cursor.getString(bodyColumn) ?: "",
                    date = cursor.getLong(dateColumn),
                    threadId = cursor.getLong(threadColumn)
                )
            }
        }
        return messages
    }
}
