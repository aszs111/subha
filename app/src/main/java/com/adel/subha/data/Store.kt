package com.adel.subha.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** ذكر واحد: نصه، وهدفه (٠ = بلا حد)، وعدده الحالي، ومجموعه الكلي. */
data class Dhikr(
    val id: Long,
    val text: String,
    val target: Int,
    val count: Int = 0,
    val total: Long = 0,
)

/** نتيجة التسبيحة الواحدة. */
enum class Tick { NORMAL, REACHED }

private val DEFAULTS = listOf(
    "سبحان الله" to 33,
    "الحمد لله" to 33,
    "الله أكبر" to 33,
    "سبحان الله وبحمده" to 100,
    "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير" to 100,
    "اللهم صلِّ على نبينا محمد" to 100,
)

/** حالة التطبيق كلها، تُحفظ في SharedPreferences عند كل تغيير. */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("subha", Context.MODE_PRIVATE)

    val adhkar = mutableStateListOf<Dhikr>()
    var currentId by mutableStateOf(0L); private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var vibrate by mutableStateOf(prefs.getBoolean("vibrate", true)); private set
    var keepOn by mutableStateOf(prefs.getBoolean("keepOn", true)); private set
    var autoNext by mutableStateOf(prefs.getBoolean("autoNext", false)); private set
    var beadStyle by mutableStateOf(prefs.getString("bead", "emerald") ?: "emerald"); private set

    init {
        val raw = prefs.getString("adhkar", null)
        if (raw == null) {
            DEFAULTS.forEachIndexed { i, (t, n) -> adhkar.add(Dhikr(i + 1L, t, n)) }
        } else {
            runCatching {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    adhkar.add(Dhikr(o.getLong("id"), o.getString("t"), o.optInt("g", 0), o.optInt("c", 0), o.optLong("s", 0)))
                }
            }
            if (adhkar.isEmpty()) DEFAULTS.forEachIndexed { i, (t, n) -> adhkar.add(Dhikr(i + 1L, t, n)) }
        }
        currentId = prefs.getLong("current", adhkar.first().id)
        if (adhkar.none { it.id == currentId }) currentId = adhkar.first().id
        saveList()
    }

    val current: Dhikr get() = adhkar.firstOrNull { it.id == currentId } ?: adhkar.first()

    private fun saveList() {
        val a = JSONArray()
        adhkar.forEach { d ->
            a.put(JSONObject().put("id", d.id).put("t", d.text).put("g", d.target).put("c", d.count).put("s", d.total))
        }
        prefs.edit().putString("adhkar", a.toString()).putLong("current", currentId).apply()
    }

    private fun replace(d: Dhikr) {
        val i = adhkar.indexOfFirst { it.id == d.id }
        if (i >= 0) adhkar[i] = d
        saveList()
    }

    /** خرزة سقطت: زد العدد. */
    fun tick(): Tick {
        val d = current
        val n = d.copy(count = d.count + 1, total = d.total + 1)
        replace(n)
        return if (n.target > 0 && n.count == n.target) Tick.REACHED else Tick.NORMAL
    }

    /** الانتقال إلى الذكر التالي في القائمة (للورد المتتابع) وبدء عدّه من الصفر. */
    fun goNext() {
        if (adhkar.size < 2) return
        val i = adhkar.indexOfFirst { it.id == currentId }
        val next = adhkar[(i + 1) % adhkar.size]
        currentId = next.id
        replace(next.copy(count = 0))
    }

    fun reset(): Int {
        val d = current
        replace(d.copy(count = 0))
        return d.count
    }

    fun restoreCount(n: Int) = replace(current.copy(count = n))

    fun select(id: Long) { currentId = id; saveList() }

    fun add(text: String, target: Int) {
        val id = (adhkar.maxOfOrNull { it.id } ?: 0L) + 1
        adhkar.add(Dhikr(id, text.trim(), target))
        saveList()
    }

    fun update(id: Long, text: String, target: Int) {
        val d = adhkar.firstOrNull { it.id == id } ?: return
        replace(d.copy(text = text.trim(), target = target))
    }

    fun setTarget(target: Int) = replace(current.copy(target = target))

    fun delete(id: Long) {
        if (adhkar.size <= 1) return
        adhkar.removeAll { it.id == id }
        if (currentId == id) currentId = adhkar.first().id
        saveList()
    }

    fun move(id: Long, delta: Int) {
        val i = adhkar.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in adhkar.indices) return
        val d = adhkar.removeAt(i)
        adhkar.add(j, d)
        saveList()
    }

    fun clearTotal(id: Long) {
        val d = adhkar.firstOrNull { it.id == id } ?: return
        replace(d.copy(total = 0))
    }

    fun setSound(v: Boolean) { sound = v; prefs.edit().putBoolean("sound", v).apply() }
    fun setVibrate(v: Boolean) { vibrate = v; prefs.edit().putBoolean("vibrate", v).apply() }
    fun setKeepOn(v: Boolean) { keepOn = v; prefs.edit().putBoolean("keepOn", v).apply() }
    fun setAutoNext(v: Boolean) { autoNext = v; prefs.edit().putBoolean("autoNext", v).apply() }
    fun setBead(id: String) { beadStyle = id; prefs.edit().putString("bead", id).apply() }
}

/** أرقام عربية مشرقية: ١٢٣ */
fun Number.ar(): String = toString().map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("")
