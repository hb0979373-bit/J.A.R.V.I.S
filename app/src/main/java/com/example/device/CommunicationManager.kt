package com.example.device

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

data class ContactInfo(
    val id: String,
    val name: String,
    val phoneNumber: String
)

data class PendingCommunicationAction(
    val type: CommunicationType,
    val targetName: String,
    val targetNumber: String,
    val messageBody: String = "",
    val actionSummary: String
)

enum class CommunicationType {
    PHONE_CALL,
    SEND_SMS,
    DELETE_DATA,
    SYSTEM_SETTING
}

class CommunicationManager(private val context: Context) {

    fun searchContacts(query: String): List<ContactInfo> {
        val results = mutableListOf<ContactInfo>()
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return results

        val contentResolver: ContentResolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getString(idIdx) else ""
                    val name = if (nameIdx != -1) it.getString(nameIdx) else ""
                    val number = if (numberIdx != -1) it.getString(numberIdx) else ""
                    if (name.isNotBlank()) {
                        results.add(ContactInfo(id = id, name = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }

        return results.distinctBy { it.name.lowercase() }
    }

    fun createCallIntent(phoneNumber: String): Intent {
        val cleanNumber = phoneNumber.replace(" ", "").replace("-", "")
        return Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$cleanNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createSmsIntent(phoneNumber: String, message: String): Intent {
        val cleanNumber = phoneNumber.replace(" ", "").replace("-", "")
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$cleanNumber")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun executePhoneCall(phoneNumber: String): Boolean {
        return try {
            val intent = createCallIntent(phoneNumber)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun executeSendSms(phoneNumber: String, message: String): Boolean {
        return try {
            val intent = createSmsIntent(phoneNumber, message)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
