package com.adel.subha.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.adel.subha.R

/** نوع من الخرز: صوره مولّدة مسبقًا (tools/render_beads.py)، ولون خيطه ولونه المميز. */
data class BeadStyle(
    val id: String,
    val name: String,
    val thread: Color,
    val accent: Color,
    val translucent: Boolean = false,
    val button: Color = accent,
)

val BEAD_STYLES = listOf(
    BeadStyle("olive", "زيتون", Color(0xFFC9A25A), Color(0xFFE2C27A), button = Color(0xFFB8873F)),
    BeadStyle("amber", "كهرمان", Color(0xFF8E1F1F), Color(0xFFF0A63A), translucent = true, button = Color(0xFFE27A12)),
    BeadStyle("turquoise", "فيروز", Color(0xFF3E6FA8), Color(0xFF4FD4E8), button = Color(0xFF27A7C2)),
    BeadStyle("yusr", "يُسر", Color(0xFFA08A55), Color(0xFFD9C08A), button = Color(0xFF3A3530)),
    BeadStyle("agate", "عقيق", Color(0xFFD9B95B), Color(0xFFF08A6A), button = Color(0xFFB8301F)),
    BeadStyle("lapis", "لازورد", Color(0xFFD9B95B), Color(0xFF9DB4FF), button = Color(0xFF2B48B0)),
    BeadStyle("pearl", "لؤلؤ", Color(0xFFE8E0CC), Color(0xFFF3E6BE), button = Color(0xFFE6DDCB)),
    BeadStyle("jade", "يَشْم", Color(0xFFD9B95B), Color(0xFF7BD9A0), button = Color(0xFF2F8F5B)),
)

fun beadStyle(id: String) = BEAD_STYLES.firstOrNull { it.id == id } ?: BEAD_STYLES.first()

private val RES = mapOf(
    "olive" to intArrayOf(R.drawable.bead_olive_0, R.drawable.bead_olive_1, R.drawable.bead_olive_2, R.drawable.disc_olive, R.drawable.imam_olive),
    "amber" to intArrayOf(R.drawable.bead_amber_0, R.drawable.bead_amber_1, R.drawable.bead_amber_2, R.drawable.disc_amber, R.drawable.imam_amber),
    "turquoise" to intArrayOf(R.drawable.bead_turquoise_0, R.drawable.bead_turquoise_1, R.drawable.bead_turquoise_2, R.drawable.disc_turquoise, R.drawable.imam_turquoise),
    "yusr" to intArrayOf(R.drawable.bead_yusr_0, R.drawable.bead_yusr_1, R.drawable.bead_yusr_2, R.drawable.disc_yusr, R.drawable.imam_yusr),
    "agate" to intArrayOf(R.drawable.bead_agate_0, R.drawable.bead_agate_1, R.drawable.bead_agate_2, R.drawable.disc_agate, R.drawable.imam_agate),
    "lapis" to intArrayOf(R.drawable.bead_lapis_0, R.drawable.bead_lapis_1, R.drawable.bead_lapis_2, R.drawable.disc_lapis, R.drawable.imam_lapis),
    "pearl" to intArrayOf(R.drawable.bead_pearl_0, R.drawable.bead_pearl_1, R.drawable.bead_pearl_2, R.drawable.disc_pearl, R.drawable.imam_pearl),
    "jade" to intArrayOf(R.drawable.bead_jade_0, R.drawable.bead_jade_1, R.drawable.bead_jade_2, R.drawable.disc_jade, R.drawable.imam_jade),
)

class BeadArt(val style: BeadStyle, val beads: List<ImageBitmap>, val disc: ImageBitmap, val imam: ImageBitmap)

@Composable
fun rememberBeadArt(style: BeadStyle): BeadArt {
    val ids = RES[style.id] ?: RES.getValue("olive")
    val b0 = ImageBitmap.imageResource(ids[0])
    val b1 = ImageBitmap.imageResource(ids[1])
    val b2 = ImageBitmap.imageResource(ids[2])
    val disc = ImageBitmap.imageResource(ids[3])
    val imam = ImageBitmap.imageResource(ids[4])
    return remember(style.id, b0, disc, imam) { BeadArt(style, listOf(b0, b1, b2), disc, imam) }
}
