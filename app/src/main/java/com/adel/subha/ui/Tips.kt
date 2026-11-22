package com.adel.subha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.adel.subha.data.ar

/** تلميح واحد يشير إلى عنصر في الشاشة. */
data class Tip(val target: String, val title: String, val body: String)

val MAIN_TIPS = listOf(
    Tip("strand", "اسحب الخرزة", "اسحب الخرزة السفلى من المجموعة العليا إلى الأسفل بإصبعك، فتسقط وتُحسب. كل سحبة تُسقط خرزة واحدة فقط، منعًا للخطأ في العدّ."),
    Tip("strand", "شكل الخرز", "اضغط على الخرز ضغطة خفيفة دون سحب لتختار نوعه: زيتون، كهرمان، فيروز، يُسر وغيرها. ولكل ذكر خرزه الخاص."),
    Tip("dhikr", "الأذكار", "اضغط على الذكر لفتح قائمة الأذكار: تختار منها، وتضيف وتعدّل، وتحدد عدد كل ذكر. واسحب على الذكر يمينًا أو يسارًا للتنقل بين الأذكار."),
    Tip("count", "العدد", "هنا عدد ما سبّحت، وتحته العدد المطلوب. والمعيّنات الثلاث تمتلئ كلما أتممت ثلثه، وعند الإتمام ينتقل التطبيق إلى الذكر التالي."),
    Tip("tap", "زر النقر", "اضغط هذا الزر لتُسقط خرزة إن لم ترد السحب.\nواضغط عليه مطولًا لتصفير العداد."),
    Tip("mic", "العدّ بالصوت", "سجّل بصمة الذكر بصوتك ثلاث مرات، ثم اضغط هنا وقل الذكر: تسقط خرزة مع كل مرة يطابق فيها نطقك بصمتك، ولا يستجيب لغيره."),
    Tip("volume", "مستوى الصوت", "اسحب هذا الشريط لتخفض صوت فرقعة الخرز أو ترفعه."),
    Tip("reset", "إعادة", "يعيد العدّ إلى الصفر، ويظهر بعده زر «تراجع» إن ضغطته خطأً."),
    Tip("settings", "الإعدادات", "الصوت والاهتزاز، والخطة وعدد المرات، والعدّ بالصوت، والخلفية، وتأرجح الخيط مع ميل الجوال، وإعادة عرض هذه التلميحات."),
)

/** مواضع العناصر التي تشير إليها التلميحات (بإحداثيات الشاشة). */
class TipTargets { val rects: SnapshotStateMap<String, Rect> = mutableStateMapOf() }

val LocalTipTargets = staticCompositionLocalOf<TipTargets?> { null }

/** يسجّل موضع العنصر ليشير إليه التلميح. */
@Composable
fun Modifier.tipTarget(id: String): Modifier {
    val t = LocalTipTargets.current ?: return this
    return this.onGloballyPositioned { t.rects[id] = it.boundsInRoot() }
}

@Composable
fun ProvideTipTargets(targets: TipTargets, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalTipTargets provides targets, content = content)

/**
 * جولة التلميحات: تعتيم الشاشة مع إبراز العنصر المقصود، وبطاقة شرح، وفي الأسفل
 * زر «لا تُظهر التلميحات مرة أخرى».
 */
@Composable
fun TipsOverlay(tips: List<Tip>, targets: TipTargets, onClose: (never: Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val tip = tips[step]
    val rect = targets.rects[tip.target]
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val appear by animateFloatAsState(1f, tween(300), label = "tip")
    fun next() { if (step < tips.lastIndex) step++ else onClose(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { next() }
    ) {
        val hPx = constraints.maxHeight.toFloat()
        // التعتيم مع فتحة حول العنصر
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.74f * appear))
            if (rect != null) {
                val r = Rect(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
                val cr = CornerRadius(minOf(r.width, r.height) / 2f, minOf(r.width, r.height) / 2f).let {
                    if (tip.target == "strand") CornerRadius(28.dp.toPx(), 28.dp.toPx()) else it
                }
                drawRoundRect(Color.Transparent, r.topLeft, r.size, cr, blendMode = BlendMode.Clear)
                drawRoundRect(Gold, r.topLeft, r.size, cr, style = Stroke(2.dp.toPx()))
            }
        }

        // بطاقة الشرح: بجانب العنصر الطويل، وتحت العنصر العلوي، وفوق العنصر السفلي
        val wPx = constraints.maxWidth.toFloat()
        val tall = rect != null && rect.height > hPx * 0.5f
        val card = @Composable {
            TipCard(
                tip, step, tips.size,
                onPrev = { if (step > 0) step-- },
                onNext = { next() },
                onSkip = { onClose(false) },
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                rect == null -> Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).offset { IntOffset(0, (hPx * 0.35f).toInt()) }) { card() }
                tall -> {
                    // العنصر على اليمين: البطاقة في المساحة اليسرى
                    val startGap = with(density) { (wPx - rect.left + pad * 2).toDp() }
                    Box(
                        Modifier.fillMaxWidth().padding(start = startGap, end = 14.dp)
                            .offset { IntOffset(0, (hPx * 0.26f).toInt()) }
                    ) { card() }
                }
                rect.center.y < hPx * 0.45f -> {
                    val y = minOf(rect.bottom + pad * 3, hPx * 0.6f)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).offset { IntOffset(0, y.toInt()) }) { card() }
                }
                else -> {
                    val bottomGap = with(density) { (hPx - rect.top + pad * 3).toDp() }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = bottomGap)) { card() }
                }
            }
        }

        // زر عدم الإظهار مرة أخرى في أسفل الشاشة
        OutlinedButton(
            onClick = { onClose(true) },
            border = BorderStroke(1.dp, Cream.copy(alpha = 0.5f)),
            // في الأسفل، إلا إذا كان العنصر المقصود هناك فيُنقل إلى الأعلى
            modifier = Modifier
                .align(if (rect != null && !tall && rect.bottom > hPx * 0.85f) Alignment.TopCenter else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 10.dp),
        ) { Text("لا تُظهر التلميحات مرة أخرى", color = Cream, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun TipCard(tip: Tip, step: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1B1A16),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tip.title, color = Gold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${(step + 1).ar()} / ${total.ar()}", color = Cream.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(tip.body, color = Cream, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) { Text("تخطَّ", color = Cream.copy(alpha = 0.6f)) }
                Spacer(Modifier.weight(1f))
                if (step > 0) TextButton(onClick = onPrev) { Text("السابق", color = GoldPale) }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onNext) { Text(if (step == total - 1) "تمّ" else "التالي", color = Gold, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
