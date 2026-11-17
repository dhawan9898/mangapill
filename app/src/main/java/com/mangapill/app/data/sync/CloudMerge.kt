package com.mangapill.app.data.sync

import org.json.JSONArray
import org.json.JSONObject

/** How one synced collection is shaped and merged. */
sealed interface CollectionSpec {
    /** An array of records. [key] identifies one; [stamp] (ms) says which copy is newer. */
    class Records(val key: (JSONObject) -> String, val stamp: ((JSONObject) -> Long)? = null) : CollectionSpec

    /** An object used as a map of records. */
    object Lookup : CollectionSpec

    /** One value replaced as a whole (settings). */
    object Blob : CollectionSpec
}

/**
 * Three-way merge of what this device has ([local]), what the cloud has ([remote]) and what both
 * agreed on at the last sync ([base]). An entry on one side only is new there, unless base had it:
 * then the other side removed it and the removal wins - except over a copy changed since. The same
 * rules as cloudSync.ts in AniStream and CineStream.
 */
object CloudMerge {
    /** A text that is equal for equal JSON values, whatever the key order or number type. */
    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { "${JSONObject.quote(it)}:${canonical(value.opt(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is Number -> value.toDouble().toString()
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private fun same(a: Any?, b: Any?) = canonical(a) == canonical(b)

    private fun absent(value: Any?) = value == null || value == JSONObject.NULL

    private fun mergeEntries(
        base: Map<String, Any>, local: Map<String, Any>, remote: Map<String, Any>, stamp: ((Any) -> Long)?
    ): LinkedHashMap<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val at = { v: Any -> stamp?.invoke(v) ?: 0L }
        for (key in LinkedHashSet(local.keys + remote.keys)) {
            val l = local[key]
            val r = remote[key]
            if (l != null && r != null) {
                out[key] = if (at(r) > at(l)) r else l
            } else {
                val only = l ?: r ?: continue
                val known = base[key]
                val changedSince = known != null && (if (stamp != null) at(only) > at(known) else !same(only, known))
                if (known == null || changedSince) out[key] = only
            }
        }
        return out
    }

    private fun records(value: Any?, key: (JSONObject) -> String): Map<String, Any> {
        val array = value as? JSONArray ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (i in 0 until array.length()) array.optJSONObject(i)?.let { out[key(it)] = it }
        return out
    }

    private fun lookup(value: Any?): Map<String, Any> {
        val obj = value as? JSONObject ?: return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (k in obj.keys()) obj.opt(k)?.takeIf { it != JSONObject.NULL }?.let { out[k] = it }
        return out
    }

    /** The merged value of one collection. An absent [remote] means the cloud has nothing yet. */
    fun merge(spec: CollectionSpec, base: Any?, local: Any?, remote: Any?): Any? {
        if (absent(remote)) return local
        return when (spec) {
            // Changed here since the last sync: this device wins. Otherwise take the cloud's copy.
            is CollectionSpec.Blob -> if (absent(base) || same(local, base)) remote else local
            is CollectionSpec.Lookup -> JSONObject().also { out ->
                mergeEntries(lookup(base), lookup(local), lookup(remote), null).forEach { (k, v) -> out.put(k, v) }
            }
            is CollectionSpec.Records -> {
                val stamp = spec.stamp
                val merged = mergeEntries(
                    records(base, spec.key), records(local, spec.key), records(remote, spec.key),
                    stamp?.let { s -> { v: Any -> s(v as JSONObject) } }
                ).values.map { it as JSONObject }
                JSONArray(if (stamp != null) merged.sortedByDescending { stamp(it) } else merged)
            }
        }
    }
}
