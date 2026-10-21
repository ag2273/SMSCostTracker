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
        val threads = mutableListOf<SmsThread>()
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
            val seen = HashSet<Long>()
            val threadColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadColumn)
                if (!seen.add(threadId)) continue
                threads += SmsThread(
                    threadId = threadId,
                    address = cursor.getString(addressColumn) ?: "",
                    messageCount = countMessages(context, threadId),
                    snippet = cursor.getString(bodyColumn) ?: "",
                    lastDate = cursor.getLong(dateColumn)
                )
            }
        }
        return threads
    }

    private fun countMessages(context: Context, threadId: Long): Int {
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            null
        )?.use { cursor ->
            return cursor.count
        }
        return 0
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
