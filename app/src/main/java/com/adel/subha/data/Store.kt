package com.adel.subha.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import com.adel.subha.audio.VoiceStore
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
    val bead: String = "olive",
    val today: Int = 0,
    val day: Long = 0,
)

/** نتيجة التسبيحة الواحدة. */
enum class Tick { NORMAL, REACHED }

private data class Seed(val text: String, val target: Int, val bead: String)

private val DEFAULTS = listOf(
    Seed("سُبْحَانَ اللهِ", 33, "olive"),
    Seed("الْحَمْدُ لِلّٰهِ", 33, "olive"),
    Seed("اللهُ أَكْبَرُ", 33, "olive"),
    Seed("سُبْحَانَ اللهِ وَبِحَمْدِهِ", 100, "yusr"),
    Seed("أَسْتَغْفِرُ اللهَ", 100, "amber"),
    Seed("لَا إِلٰهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ", 100, "turquoise"),
    Seed("اللَّهُمَّ صَلِّ عَلَى نَبِيِّنَا مُحَمَّدٍ", 100, "agate"),
)

private fun seedList() = DEFAULTS.mapIndexed { i, d -> Dhikr(i + 1L, d.text, d.target, bead = d.bead) }

private fun todayEpoch(): Long = java.time.LocalDate.now().toEpochDay()

/** حالة التطبيق كلها، تُحفظ في SharedPreferences عند كل تغيير. */
class AppState(context: Context) {
    private val prefs = context.getSharedPreferences("subha", Context.MODE_PRIVATE)

    val adhkar = mutableStateListOf<Dhikr>()
    var currentId by mutableStateOf(0L); private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var vibrate by mutableStateOf(prefs.getBoolean("vibrate", true)); private set
    var keepOn by mutableStateOf(prefs.getBoolean("keepOn", true)); private set
    var autoNext by mutableStateOf(prefs.getBoolean("autoNext", true)); private set
    var voiceSensitivity by mutableStateOf(prefs.getFloat("voiceSens2", 0.75f)); private set
    /** ٠ متساهل .. ١ صارم في تمييز لفظ الذكر. */
    var matchStrict by mutableStateOf(prefs.getFloat("matchStrict", 0.5f)); private set
    /** بصمات الصوت المسجّلة؛ voiceRev يتغيّر عند كل حفظ أو حذف. */
    val voices = VoiceStore(context)
    var voiceRev by mutableIntStateOf(0); private set
    var sway by mutableStateOf(prefs.getBoolean("sway", true)); private set
    var volume by mutableStateOf(prefs.getFloat("volume", 0.8f)); private set
    var background by mutableStateOf(prefs.getString("bg", "black") ?: "black"); private set

    init {
        val raw = prefs.getString("adhkar2", null)
        if (raw == null) {
            adhkar.addAll(seedList())
        } else {
            runCatching {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    adhkar.add(
                        Dhikr(
                            o.getLong("id"), o.getString("t"), o.optInt("g", 0), o.optInt("c", 0), o.optLong("s", 0),
                            o.optString("b", "olive"), o.optInt("td", 0), o.optLong("dy", 0),
                        )
                    )
                }
            }
            if (adhkar.isEmpty()) adhkar.addAll(seedList())
        }
        currentId = prefs.getLong("current", adhkar.first().id)
        if (adhkar.none { it.id == currentId }) currentId = adhkar.first().id
        saveList()
    }

    val current: Dhikr get() = adhkar.firstOrNull { it.id == currentId } ?: adhkar.first()

    private fun saveList() {
        val a = JSONArray()
        adhkar.forEach { d ->
            a.put(JSONObject().put("id", d.id).put("t", d.text).put("g", d.target).put("c", d.count).put("s", d.total)
                .put("b", d.bead).put("td", d.today).put("dy", d.day))
        }
        prefs.edit().putString("adhkar2", a.toString()).putLong("current", currentId).apply()
    }

    private fun replace(d: Dhikr) {
        val i = adhkar.indexOfFirst { it.id == d.id }
        if (i >= 0) adhkar[i] = d
        saveList()
    }

    /** خرزة سقطت: زد العدد. */
    fun tick(): Tick {
        val d = current
        val td = todayEpoch()
        val n = d.copy(count = d.count + 1, total = d.total + 1, today = (if (d.day == td) d.today else 0) + 1, day = td)
        replace(n)
        return if (n.target > 0 && n.count == n.target) Tick.REACHED else Tick.NORMAL
    }

    /** الانتقال إلى الذكر التالي في القائمة (للورد المتتابع) وبدء عدّه من الصفر. */
    /** عدد اليوم لهذا الذكر (يُصفَّر مع بداية يوم جديد). */
    fun todayOf(d: Dhikr): Int = if (d.day == todayEpoch()) d.today else 0

    fun goNext(step: Int = 1) {
        if (adhkar.size < 2) return
        val i = adhkar.indexOfFirst { it.id == currentId }
        val next = adhkar[((i + step) % adhkar.size + adhkar.size) % adhkar.size]
        currentId = next.id
        replace(next.copy(count = 0))
    }

    /** تنقّل يدوي بين الأذكار دون تصفير. */
    fun browse(step: Int) {
        if (adhkar.size < 2) return
        val i = adhkar.indexOfFirst { it.id == currentId }
        currentId = adhkar[((i + step) % adhkar.size + adhkar.size) % adhkar.size].id
        saveList()
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
        adhkar.add(Dhikr(id, text.trim(), target, bead = current.bead))
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
        voices.delete(id); voiceRev++
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

    fun changeSound(v: Boolean) { sound = v; prefs.edit().putBoolean("sound", v).apply() }
    fun changeVibrate(v: Boolean) { vibrate = v; prefs.edit().putBoolean("vibrate", v).apply() }
    fun changeKeepOn(v: Boolean) { keepOn = v; prefs.edit().putBoolean("keepOn", v).apply() }
    fun changeAutoNext(v: Boolean) { autoNext = v; prefs.edit().putBoolean("autoNext", v).apply() }
    fun setBead(style: String, all: Boolean = false) {
        if (all) {
            for (i in adhkar.indices) adhkar[i] = adhkar[i].copy(bead = style)
            saveList()
        } else replace(current.copy(bead = style))
    }
    fun changeVoiceSensitivity(v: Float) { voiceSensitivity = v; prefs.edit().putFloat("voiceSens2", v).apply() }
    fun changeMatchStrict(v: Float) { matchStrict = v; prefs.edit().putFloat("matchStrict", v).apply() }
    /** حدّ القبول في المطابقة: من ١٫٣٨ (متساهل) إلى ١٫١٠ (صارم). */
    val strictValue: Float get() = 1.38f - 0.28f * matchStrict
    fun saveVoice(id: Long, templates: List<Array<FloatArray>>, durMs: Float) { voices.save(id, templates, durMs); voiceRev++ }
    fun deleteVoice(id: Long) { voices.delete(id); voiceRev++ }
    fun changeSway(v: Boolean) { sway = v; prefs.edit().putBoolean("sway", v).apply() }
    fun changeVolume(v: Float) { volume = v; prefs.edit().putFloat("volume", v).apply() }
    fun changeBackground(v: String) { background = v; prefs.edit().putString("bg", v).apply() }
}

/** أرقام عربية مشرقية: ١٢٣ */
fun Number.ar(): String = toString().map { c -> if (c in '0'..'9') '٠' + (c - '0') else c }.joinToString("")
