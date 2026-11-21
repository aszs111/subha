package com.adel.subha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adel.subha.BuildConfig
import com.adel.subha.data.AppState
import com.adel.subha.data.Dhikr
import com.adel.subha.data.ar

private val PRESETS = listOf(33, 100, 300, 500, 1000)

/** يحوّل الأرقام العربية المكتوبة إلى رقم. */
private fun parseNum(s: String): Int? =
    s.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("").filter { it.isDigit() }.take(7).toIntOrNull()

@Composable
private fun Bar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = Gold, navigationIconContentColor = GoldPale),
    )
}

@Composable
private fun TargetChips(target: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (PRESETS + 0).forEach { n ->
            FilterChip(
                selected = target == n,
                onClick = { onPick(n) },
                label = { Text(if (n == 0) "بلا حد" else n.ar()) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Color(0xFF1A1405)),
            )
        }
    }
}

// ───────────────────────── شاشة الأذكار ─────────────────────────

@Composable
fun AdhkarScreen(state: AppState, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<Dhikr?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { Bar("الأذكار", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("إضافة ذكر") },
                containerColor = Gold, contentColor = Color(0xFF1A1405),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = pad.calculateTopPadding() + 10.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.adhkar, key = { it.id }) { d ->
                val selected = d.id == state.currentId
                Surface(
                    onClick = { state.select(d.id); onBack() },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Gold else Gold.copy(alpha = 0.2f)),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(d.text, style = MaterialTheme.typography.titleLarge, color = Cream)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "الهدف: ${if (d.target > 0) d.target.ar() else "بلا حد"}  ·  العدد: ${d.count.ar()}  ·  المجموع: ${d.total.ar()}",
                                style = MaterialTheme.typography.bodyMedium, color = GoldPale.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = { editing = d }) { Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = GoldPale) }
                    }
                }
            }
        }
    }

    if (adding) DhikrDialog(null, state, onDismiss = { adding = false })
    editing?.let { d -> DhikrDialog(d, state, onDismiss = { editing = null }) }
}

@Composable
private fun DhikrDialog(d: Dhikr?, state: AppState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(d?.text ?: "") }
    var target by remember { mutableStateOf(d?.target ?: 100) }
    var targetText by remember { mutableStateOf(if ((d?.target ?: 100) > 0) (d?.target ?: 100).toString() else "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (d == null) "ذكر جديد" else "تعديل الذكر") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(text, { text = it }, label = { Text("نص الذكر") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("عدد المرات (الخطة)", style = MaterialTheme.typography.titleSmall, color = Gold)
                Spacer(Modifier.height(6.dp))
                TargetChips(target) { target = it; targetText = if (it > 0) it.toString() else "" }
                OutlinedTextField(
                    targetText,
                    { v -> targetText = v; target = parseNum(v) ?: 0 },
                    label = { Text("عدد مخصص (فارغ = بلا حد)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (d != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { state.move(d.id, -1) }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "تقديم") }
                        IconButton(onClick = { state.move(d.id, 1) }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تأخير") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { state.clearTotal(d.id) }) { Text("تصفير المجموع") }
                    }
                    if (state.adhkar.size > 1) {
                        TextButton(onClick = { confirmDelete = true }) { Text("حذف هذا الذكر", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                if (d == null) state.add(text, target) else state.update(d.id, text, target)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الذكر؟") },
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = { state.delete(d.id); confirmDelete = false; onDismiss() }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

// ───────────────────────── شاشة شكل الخرزات ─────────────────────────

@Composable
fun BeadsScreen(state: AppState, onBack: () -> Unit) {
    val d = state.current
    val edge = edgeColor(state.background)
    Scaffold(topBar = { Bar("شكل الخرز", onBack) }, containerColor = edge) { pad ->
        Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
            Text(
                d.text, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleMedium, color = Cream.copy(alpha = 0.75f), textAlign = TextAlign.Center, maxLines = 2,
            )
            LazyRow(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(BEAD_STYLES, key = { it.id }) { st ->
                    val selected = st.id == d.bead
                    val art = rememberBeadArt(st)
                    Column(
                        Modifier
                            .width(104.dp)
                            .fillMaxHeight()
                            .clickable { state.setBead(st.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StrandPreview(art, edge, Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(st.name, style = MaterialTheme.typography.titleMedium, color = if (selected) st.accent else Cream.copy(alpha = 0.7f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        Box(
                            Modifier.padding(top = 4.dp).width(28.dp).height(3.dp)
                                .background(if (selected) st.accent else Color.Transparent, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            TextButton(
                onClick = { state.setBead(d.bead, all = true) },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            ) { Text("استعمال هذا الخرز لكل الأذكار") }
        }
    }
}

// ───────────────────────── الإعدادات ─────────────────────────

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit, onBeads: () -> Unit, onAdhkar: () -> Unit) {
    val d = state.current
    var custom by remember(d.id) { mutableStateOf("") }
    Scaffold(topBar = { Bar("الإعدادات", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp)
        ) {
            Section("التنبيه")
            SwitchRow("صوت فرقعة الخرز", null, state.sound, state::changeSound)
            SwitchRow("الاهتزاز مع كل خرزة", "واهتزاز أطول عند إتمام العدد", state.vibrate, state::changeVibrate)
            SwitchRow("إبقاء الشاشة مضاءة", "أثناء التسبيح في الشاشة الرئيسية", state.keepOn, state::changeKeepOn)
            SwitchRow("تأرجح الخيط مع ميل الجوال", "يتدلى الخيط ويتأرجح كالمسبحة الحقيقية", state.sway, state::changeSway)

            Section("العدّ بالصوت")
            Text(
                "اضغط زر الميكروفون في الشاشة الرئيسية، ثم قل الذكر واسكت لحظة بعد كل مرة، فتسقط خرزة مع كل تكرار. " +
                    "يعمل على الجهاز دون إنترنت ولا يحفظ الصوت. وهو يعدّ النطق لا الكلمات، فأي كلام آخر قد يُحسب.",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium, color = GoldPale.copy(alpha = 0.75f),
            )
            Text("الحساسية", Modifier.padding(start = 20.dp, top = 4.dp), style = MaterialTheme.typography.titleSmall, color = Cream)
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("منخفضة", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.6f))
                Slider(
                    value = state.voiceSensitivity, onValueChange = { state.changeVoiceSensitivity(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("عالية", style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.6f))
            }
            Text(
                "ارفعها إن كان صوتك خافتًا، واخفضها في المكان المزعج.",
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall, color = Cream.copy(alpha = 0.5f),
            )

            Section("الخطة")
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("الذكر الحالي: ${d.text}", style = MaterialTheme.typography.titleMedium, color = Cream)
                Spacer(Modifier.height(4.dp))
                Text(
                    "أريد أن أذكره: ${if (d.target > 0) "${d.target.ar()} مرة" else "بلا حد"}",
                    style = MaterialTheme.typography.bodyLarge, color = GoldPale.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                TargetChips(d.target) { state.setTarget(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        custom, { custom = it },
                        label = { Text("عدد مخصص") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(enabled = parseNum(custom) != null, onClick = { parseNum(custom)?.let { state.setTarget(it) }; custom = "" }) { Text("تعيين") }
                }
            }
            SwitchRow(
                "الانتقال تلقائيًا إلى الذكر التالي عند الإتمام",
                "لورد متتابع، كأذكار ما بعد الصلاة: التسبيح ثم التحميد ثم التكبير، بترتيب قائمة الأذكار",
                state.autoNext, state::changeAutoNext,
            )

            Section("المظهر والأذكار")
            NavRow("شكل الخرز للذكر الحالي", beadStyle(d.bead).name, onBeads)
            Text("الخلفية", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = Cream)
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("black" to "سوداء", "green" to "خضراء ملكية").forEach { (id, name) ->
                    FilterChip(
                        selected = state.background == id,
                        onClick = { state.changeBackground(id) },
                        label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Color(0xFF1A1405)),
                    )
                }
            }
            NavRow("الأذكار", "${state.adhkar.size.ar()} أذكار — إضافة وتعديل وترتيب", onAdhkar)

            Spacer(Modifier.height(24.dp))
            Text(
                "سُبحة · الإصدار ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = Cream.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Gold.copy(alpha = 0.2f))
}

@Composable
private fun SwitchRow(title: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (sub != null) ({ Text(sub) }) else null,
        trailingContent = {
            Switch(value, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1A1405), checkedTrackColor = Gold))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun NavRow(title: String, sub: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(sub) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Gold) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
