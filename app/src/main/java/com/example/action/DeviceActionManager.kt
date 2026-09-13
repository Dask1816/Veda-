package com.example.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.models.ContactInfo
import com.example.data.models.DeviceActionResult

class DeviceActionManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceActionManager"

        private val ALLOWED_APPS = mapOf(
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "spotify" to listOf("com.spotify.music"),
            "chrome" to listOf("com.android.chrome"),
            "camera" to listOf("android.media.action.STILL_IMAGE_CAMERA"),
            "settings" to listOf("android.settings.SETTINGS"),
            "phone" to listOf("android.intent.action.DIAL"),
            "dialer" to listOf("android.intent.action.DIAL"),
            "contacts" to listOf("content://contacts/people"),
            "calculator" to listOf("com.google.android.calculator"),
            "clock" to listOf("android.intent.action.SHOW_ALARMS")
        )
    }

    fun openWhatsApp(): DeviceActionResult {
        Log.d(TAG, "Executing openWhatsApp")
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage("com.whatsapp")
                ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                DeviceActionResult(true, "openWhatsApp", "WhatsApp opened successfully on your device.")
            } else {
                // Fallback to web WhatsApp link
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                DeviceActionResult(true, "openWhatsApp", "WhatsApp app not found; opening WhatsApp Web in browser.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open WhatsApp: ${e.message}", e)
            DeviceActionResult(false, "openWhatsApp", "Could not open WhatsApp: ${e.message}", error = e.message)
        }
    }

    fun openApp(appName: String): DeviceActionResult {
        val cleanName = appName.trim().lowercase()
        Log.d(TAG, "Executing openApp: $cleanName")

        if (cleanName.contains("whatsapp")) {
            return openWhatsApp()
        }

        // Check common system shortcuts first
        when {
            cleanName.contains("camera") -> {
                return try {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Camera opened.")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Failed to open camera: ${e.message}", error = e.message)
                }
            }
            cleanName.contains("setting") -> {
                return try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Settings opened.")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Failed to open settings: ${e.message}", error = e.message)
                }
            }
            cleanName.contains("clock") || cleanName.contains("alarm") -> {
                return try {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Clock / Alarms opened.")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Failed to open clock: ${e.message}", error = e.message)
                }
            }
            cleanName.contains("phone") || cleanName.contains("dialer") -> {
                return try {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Phone dialer opened.")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Failed to open dialer: ${e.message}", error = e.message)
                }
            }
        }

        // Allowlist lookup
        val packageCandidates = ALLOWED_APPS.entries
            .firstOrNull { cleanName.contains(it.key) || it.key.contains(cleanName) }
            ?.value

        val pm = context.packageManager

        if (packageCandidates != null) {
            for (pkg in packageCandidates) {
                try {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return DeviceActionResult(true, "openApp", "Opened $appName successfully.")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Candidate $pkg failed: ${e.message}")
                }
            }
        }

        // Try searching installed applications
        try {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label == cleanName || label.contains(cleanName)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return DeviceActionResult(true, "openApp", "Opened ${pm.getApplicationLabel(app)}.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps: ${e.message}", e)
        }

        return DeviceActionResult(
            false,
            "openApp",
            "App '$appName' is not installed or cannot be opened on this device.",
            error = "App not found"
        )
    }

    fun openUrl(url: String): DeviceActionResult {
        Log.d(TAG, "Executing openUrl: $url")
        var targetUrl = url.trim()
        if (!targetUrl.startsWith("http://", ignoreCase = true) && !targetUrl.startsWith("https://", ignoreCase = true)) {
            targetUrl = "https://$targetUrl"
        }

        return try {
            val uri = Uri.parse(targetUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(true, "openUrl", "Opened $targetUrl in browser.", data = targetUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open url $targetUrl: ${e.message}", e)
            DeviceActionResult(false, "openUrl", "Failed to open URL: ${e.message}", error = e.message)
        }
    }

    fun makeCall(phoneNumber: String): DeviceActionResult {
        Log.d(TAG, "Executing makeCall: $phoneNumber")
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            return DeviceActionResult(false, "makeCall", "Invalid phone number provided.", error = "Invalid number")
        }

        return try {
            val uri = Uri.parse("tel:$cleanNumber")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DeviceActionResult(
                true,
                "makeCall",
                "Opened phone dialer for $cleanNumber.",
                data = cleanNumber
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call to $cleanNumber: ${e.message}", e)
            DeviceActionResult(false, "makeCall", "Could not start call: ${e.message}", error = e.message)
        }
    }

    fun searchContacts(queryName: String): List<ContactInfo> {
        val trimmed = queryName.trim().lowercase()
        val results = mutableListOf<ContactInfo>()

        // Check contact permission
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.w(TAG, "READ_CONTACTS permission not granted")
            return emptyList()
        }

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIndex != -1) it.getString(idIndex) else ""
                    val name = if (nameIndex != -1) it.getString(nameIndex) else ""
                    val number = if (numberIndex != -1) it.getString(numberIndex) else ""

                    if (name.lowercase().contains(trimmed) || trimmed.contains(name.lowercase())) {
                        if (results.none { existing -> existing.phoneNumber == number }) {
                            results.add(ContactInfo(id = id, name = name, phoneNumber = number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts: ${e.message}", e)
        }

        return results
    }

    fun callContact(contactName: String): DeviceActionResult {
        Log.d(TAG, "Executing callContact: $contactName")
        val matches = searchContacts(contactName)

        return when {
            matches.isEmpty() -> {
                DeviceActionResult(
                    false,
                    "callContact",
                    "No contact named '$contactName' was found in your device contacts.",
                    error = "Contact not found"
                )
            }
            matches.size == 1 -> {
                val single = matches.first()
                val callRes = makeCall(single.phoneNumber)
                DeviceActionResult(
                    callRes.success,
                    "callContact",
                    "Calling ${single.name} (${single.phoneNumber}).",
                    data = single
                )
            }
            else -> {
                // Multiple matches found - need disambiguation
                val names = matches.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                DeviceActionResult(
                    false,
                    "callContact",
                    "Found ${matches.size} contacts for '$contactName': $names. Which one would you like to call?",
                    error = "Multiple matching contacts found",
                    data = matches
                )
            }
        }
    }
}
