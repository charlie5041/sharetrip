package tw.tripshare.core

/**
 * Reading Firestore documents as plain maps. Numbers arrive as Long on
 * Android and as Double on the web, so every number goes through [long].
 */
typealias Doc = Map<String, Any?>

internal fun Doc.str(key: String): String =
    this[key] as? String ?: throw IllegalArgumentException("欄位 $key 不是文字")

internal fun Doc.strOrNull(key: String): String? = this[key] as? String

internal fun Doc.long(key: String): Long = (this[key] as? Number)?.toLong()
    ?: throw IllegalArgumentException("欄位 $key 不是數字")

internal fun Doc.longOr(key: String, default: Long): Long =
    (this[key] as? Number)?.toLong() ?: default

internal fun Doc.boolOr(key: String, default: Boolean): Boolean = this[key] as? Boolean ?: default

internal fun Doc.strings(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

internal fun Doc.longMap(key: String): Map<String, Long> =
    (this[key] as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is Number) k to v.toLong() else null }
        ?.toMap() ?: emptyMap()
