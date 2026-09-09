package com.soniccast.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.soniccast.app.data.protocol.Packet
import org.json.JSONArray
import org.json.JSONObject

/**
 * Model representing the latest broadcast message stored locally for Surprise Challenge 2 (Dynamic Group).
 */
data class LatestMessage(
    val messageId: Int,
    val version: Int,
    val totalPackets: Int,
    val text: String,
    val packets: List<Packet>,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Offline persistent storage for the latest broadcast message (Surprise Challenge 2).
 *
 * Requirements satisfied:
 * - Maintains latest message ID, version/sequence number, total packets, and packet list.
 * - Survives screen navigation, role changes, and app recreation.
 * - Operates completely offline without cloud, Wi-Fi, or Bluetooth.
 */
class LatestMessageStore private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "soniccast_latest_msg_store"
        private const val KEY_MESSAGE_ID = "latest_msg_id"
        private const val KEY_VERSION = "latest_version"
        private const val KEY_TOTAL_PACKETS = "latest_total_packets"
        private const val KEY_TEXT = "latest_text"
        private const val KEY_PACKETS_JSON = "latest_packets_json"
        private const val KEY_CREATED_AT = "latest_created_at"

        @Volatile
        private var instance: LatestMessageStore? = null

        fun getInstance(context: Context): LatestMessageStore {
            return instance ?: synchronized(this) {
                instance ?: LatestMessageStore(context).also { instance = it }
            }
        }
    }

    /**
     * Stores the latest successfully broadcast message.
     */
    @Synchronized
    fun saveLatestMessage(
        messageId: Int,
        version: Int,
        totalPackets: Int,
        text: String,
        packets: List<Packet>
    ) {
        val jsonArray = JSONArray()
        for (packet in packets) {
            val obj = JSONObject().apply {
                put("type", packet.type.name)
                put("messageId", packet.messageId)
                put("version", packet.version)
                put("packetId", packet.packetId)
                put("totalPackets", packet.totalPackets)
                put("payloadHex", bytesToHex(packet.payload))
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putInt(KEY_MESSAGE_ID, messageId)
            .putInt(KEY_VERSION, version)
            .putInt(KEY_TOTAL_PACKETS, totalPackets)
            .putString(KEY_TEXT, text)
            .putString(KEY_PACKETS_JSON, jsonArray.toString())
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Retrieves the latest broadcast message, or null if none is stored yet.
     */
    @Synchronized
    fun getLatestMessage(): LatestMessage? {
        val messageId = prefs.getInt(KEY_MESSAGE_ID, -1)
        if (messageId == -1) return null

        val version = prefs.getInt(KEY_VERSION, 1)
        val totalPackets = prefs.getInt(KEY_TOTAL_PACKETS, 1)
        val text = prefs.getString(KEY_TEXT, "") ?: ""
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        val packetsJson = prefs.getString(KEY_PACKETS_JSON, "[]") ?: "[]"

        val packetList = mutableListOf<Packet>()
        try {
            val jsonArray = JSONArray(packetsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = com.soniccast.app.data.protocol.PacketType.valueOf(obj.getString("type"))
                val mId = obj.getInt("messageId")
                val ver = obj.getInt("version")
                val pId = obj.getInt("packetId")
                val tot = obj.getInt("totalPackets")
                val payload = hexToBytes(obj.optString("payloadHex", ""))

                packetList.add(Packet(type, mId, ver, pId, tot, payload))
            }
        } catch (e: Exception) {
            // Return empty list if parsing fails
        }

        return LatestMessage(
            messageId = messageId,
            version = version,
            totalPackets = totalPackets,
            text = text,
            packets = packetList,
            createdAt = createdAt
        )
    }

    /**
     * Checks if the receiver already possesses [version].
     */
    @Synchronized
    fun hasVersion(version: Int): Boolean {
        val current = prefs.getInt(KEY_VERSION, -1)
        return current == version
    }

    /**
     * Clears stored message.
     */
    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
