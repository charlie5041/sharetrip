package tw.tripshare.core

/** Why an invite code didn't work, worded for the person typing it. */
enum class InviteError(val message: String) {
    notFound("找不到這組邀請碼，請確認後重新輸入"),
    expired("這組邀請碼已過期，請對方重新產生"),
    alreadyMember("你已經在這趟旅程裡了"),
}

class InviteException(val kind: InviteError) : Exception(kind.message)

object InviteCodes {
    /** No 0/O/1/I, so a code read aloud can't be mistyped. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8

    /** Valid for 24 hours. */
    const val TTL_MILLIS = 24L * 60 * 60 * 1000

    /** A code from random bytes (one per character, from a secure source). */
    fun fromBytes(bytes: ByteArray): String {
        require(bytes.size >= LENGTH)
        // 256 is a multiple of the 32-letter alphabet, so every letter is equally likely.
        return (0 until LENGTH).joinToString("") { ALPHABET[(bytes[it].toInt() and 0xFF) % ALPHABET.length].toString() }
    }

    fun normalize(input: String): String = input.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    /** "ABCD-EFGH" */
    fun format(code: String): String = "${code.substring(0, 4)}-${code.substring(4)}"
}
