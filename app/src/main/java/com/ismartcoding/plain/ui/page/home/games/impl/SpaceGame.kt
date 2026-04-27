package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.SpaceSettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.*
import kotlin.random.Random

@Serializable
data class SpaceSettings(
    val sensitivity: Float = 0.85f,
    val fireRate: Int = 5,
    val autoFire: Boolean = true,
    val haptics: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val batterySaver: Boolean = false,
    val oneHanded: Boolean = false,
    val assistHitbox: Boolean = false,
    val assistAutoFire: Boolean = false,
    val assistInvuln: Boolean = false,
    val colorblind: String = "off", // off | protanopia | deuteranopia | tritanopia
    val skin: String = "interceptor", // interceptor | brawler | stealth | healer
    val unlocks: List<String> = listOf("interceptor"),
    val mmr: Int = 50,
    val coins: Int = 0,
    val totalKills: Int = 0,
    val upgrades: Map<String, Int> = emptyMap(),
)

private data class SShip(var x: Float, var y: Float, var hp: Int)
private data class SBullet(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val from: Int, // 0 player, 1 enemy
    val dmg: Float = 1f, val charged: Boolean = false, val homing: Boolean = false,
)
private data class SEnemy(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var hp: Int, val maxHp: Int, val kind: String,
    var cd: Int = 0, val isBoss: Boolean = false, var pattern: Int = 0,
)
private data class SDrop(var x: Float, var y: Float, val kind: String, var vy: Float = 1.6f)
private data class SPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val life0: Int, val color: Color, val size: Float)
private data class SStar(var x: Float, var y: Float, val speed: Float, val size: Float, val alpha: Float)

private val skinDefs = mapOf(
    "interceptor" to Triple(3, 1.0f, 1.0f),
    "brawler"     to Triple(4, 0.85f, 1.3f),
    "stealth"     to Triple(2, 1.25f, 0.9f),
    "healer"      to Triple(3, 0.95f, 0.85f),
)
private val skinColors = mapOf(
    "interceptor" to Pair(Color(0xFFA855F7), Color(0xFF22D3EE)),
    "brawler"     to Pair(Color(0xFFEF4444), Color(0xFFFACC15)),
    "stealth"     to Pair(Color(0xFF0EA5E9), Color(0xFF94A3B8)),
    "healer"      to Pair(Color(0xFF10B981), Color(0xFF34D399)),
)

private val perkPool = listOf(
    Triple("shieldStart", "Shield Start",   "Spawn each wave with 3-second shield."),
    Triple("lifesteal",   "Lifesteal",      "Every 25 kills heals 1 HP."),
    Triple("doubleShot",  "Double Shot",    "Permanently +1 weapon level."),
    Triple("magnet",      "Magnet",         "Stronger pull on power-ups."),
    Triple("overcharge",  "Overcharge",     "Energy fills 50% faster."),
    Triple("glassCannon", "Glass Cannon",   "+50% dmg, -1 HP."),
    Triple("lastStand",   "Last Stand",     "At 1 HP fire rate doubles."),
)

@Composable
fun SpaceGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    var settings by remember { mutableStateOf(SpaceSettings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val raw = SpaceSettingsJsonPreference.getAsync(context)
            if (raw.isNotEmpty() && raw != "{}") settings = json.decodeFromString(SpaceSettings.serializer(), raw)
        } catch (_: Exception) { /* keep default */ }
        loaded = true
    }
    fun persist() {
        scope.launch {
            try { SpaceSettingsJsonPreference.putAsync(context, json.encodeToString(settings)) } catch (_: Exception) {}
        }
    }

    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val ship = remember { SShip(0f, 0f, 3) }
    var pointerX by remember { mutableStateOf<Float?>(null) }
    var pointerY by remember { mutableStateOf<Float?>(null) }
    val bullets = remember { mutableStateListOf<SBullet>() }
    val enemies = remember { mutableStateListOf<SEnemy>() }
    val drops = remember { mutableStateListOf<SDrop>() }
    val particles = remember { mutableStateListOf<SPart>() }
    val stars = remember { mutableStateListOf<SStar>() }
    var alive by remember { mutableStateOf(true) }
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var wave by remember { mutableStateOf(1) }
    var weaponLevel by remember { mutableStateOf(1) }
    var energy by remember { mutableStateOf(0f) }
    var iframes by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(1) }
    var bestCombo by remember { mutableStateOf(1) }
    var lastKillT by remember { mutableStateOf(0L) }
    var shieldUntil by remember { mutableStateOf(0L) }
    var damageBoostUntil by remember { mutableStateOf(0L) }
    var slowMoUntil by remember { mutableStateOf(0L) }
    var bossActive by remember { mutableStateOf(false) }
    var bossWarn by remember { mutableStateOf(0) }
    var killsThisWave by remember { mutableStateOf(0) }
    var waveTarget by remember { mutableStateOf(8) }
    var totalKills by remember { mutableStateOf(0) }
    var shotsFired by remember { mutableStateOf(0) }
    var shotsHit by remember { mutableStateOf(0) }
    var spawnTimer by remember { mutableStateOf(0) }
    var fireTimer by remember { mutableStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var perkChoice by remember { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    var activePerk by remember { mutableStateOf<String?>(null) }
    var slowMul by remember { mutableStateOf(1f) }

    fun skinHp() = (skinDefs[settings.skin]?.first ?: 3) + (settings.upgrades["hp"] ?: 0)
    fun skinSpeed() = (skinDefs[settings.skin]?.second ?: 1f) * (1f + 0.05f * (settings.upgrades["speed"] ?: 0))
    fun skinFireMul() = skinDefs[settings.skin]?.third ?: 1f

    fun shotInterval(): Long {
        var base = 1000.0 / (settings.fireRate + 0.5 * (settings.upgrades["fire"] ?: 0))
        base /= skinFireMul()
        if (lives == 1 && activePerk == "lastStand") base /= 2
        return base.toLong()
    }

    fun explode(x: Float, y: Float, color: Color, n: Int = 14) {
        val cap = if (settings.batterySaver) 6 else if (settings.reducedMotion) 4 else n
        repeat(cap) {
            particles.add(SPart(x, y, (Random.nextFloat() - 0.5f) * 6f, (Random.nextFloat() - 0.5f) * 6f, 36, 36, color, 2f + Random.nextFloat() * 2f))
        }
    }

    fun shoot(charged: Boolean = false) {
        shotsFired += 1
        val sx = ship.x; val sy = ship.y - 22
        val dmg = (1 + (settings.upgrades["dmg"] ?: 0)) * (if (charged) 3f else 1f) * (if (System.currentTimeMillis() < damageBoostUntil) 2f else 1f) * (if (activePerk == "glassCannon") 1.5f else 1f)
        when (weaponLevel) {
            1 -> bullets.add(SBullet(sx, sy, 0f, -10f, 0, dmg, charged))
            2 -> { bullets.add(SBullet(sx - 8, sy, 0f, -10f, 0, dmg)); bullets.add(SBullet(sx + 8, sy, 0f, -10f, 0, dmg)) }
            3 -> { bullets.add(SBullet(sx, sy, 0f, -10f, 0, dmg)); bullets.add(SBullet(sx - 8, sy, -2f, -10f, 0, dmg)); bullets.add(SBullet(sx + 8, sy, 2f, -10f, 0, dmg)) }
            4 -> for (i in -2..2) bullets.add(SBullet(sx + i * 6f, sy, i * 1.5f, -10f, 0, dmg))
            else -> for (i in -3..3) bullets.add(SBullet(sx + i * 5f, sy, i * 0.8f, -10f, 0, dmg))
        }
    }
    fun fireOverdrive() {
        if (energy < 100f) return
        energy = 0f
        for (i in 0 until 8) {
            val a = i / 8f * (PI * 2)
            bullets.add(SBullet(ship.x, ship.y, cos(a).toFloat() * 2f, -6f, 0, 4f, false, true))
        }
    }
    fun rollPerks() {
        val pool = perkPool.shuffled().take(3)
        perkChoice = pool
    }
    fun selectPerk(id: String) {
        activePerk = id
        perkChoice = null
        if (id == "doubleShot") weaponLevel = min(5, weaponLevel + 1)
        if (id == "glassCannon") ship.hp = max(1, ship.hp - 1)
        lives = ship.hp
    }

    fun reset() {
        if (w == 0f || h == 0f) return
        bullets.clear(); enemies.clear(); drops.clear(); particles.clear(); stars.clear()
        ship.x = w / 2; ship.y = h - 100; ship.hp = skinHp(); lives = ship.hp
        score = 0; weaponLevel = 1; wave = 1; combo = 1; bestCombo = 1
        energy = 0f; iframes = 0; shieldUntil = 0L; damageBoostUntil = 0L; slowMoUntil = 0L
        bossActive = false; bossWarn = 0
        killsThisWave = 0; waveTarget = 8; totalKills = 0; shotsFired = 0; shotsHit = 0
        spawnTimer = 0; fireTimer = 0
        repeat(80) { stars.add(SStar(Random.nextFloat() * w, Random.nextFloat() * h, 0.3f + Random.nextFloat() * 1.6f, 0.4f + Random.nextFloat() * 1.7f, 0.3f + Random.nextFloat() * 0.7f)) }
        slowMul = 1f
        val mmrMod = (settings.mmr - 50) / 100f
        slowMul = 1f
        rollPerks()
    }

    fun spawnEnemy() {
        val r = Random.nextFloat()
        val mEl = if (settings.mmr > 70) 0.45f else 0f
        val kind = when {
            wave >= 5 && r < 0.06f && !bossActive -> "mother"
            wave >= 4 && r < 0.18f -> "sniper"
            wave >= 3 && r < 0.40f -> "tank"
            r < mEl -> "elite"
            else -> "swarmer"
        }
        val x = 30f + Random.nextFloat() * (w - 60f)
        val baseHp = wave / 3
        val mul = 1f + (settings.mmr - 50) / 200f
        when (kind) {
            "swarmer" -> enemies.add(SEnemy(x, -20f, (Random.nextFloat() - 0.5f) * 1.6f, 1.5f * mul, 1, 1, kind))
            "tank" -> enemies.add(SEnemy(x, -30f, (Random.nextFloat() - 0.5f) * 0.8f, 1.0f * mul, 4 + baseHp, 4 + baseHp, kind, cd = 60))
            "sniper" -> enemies.add(SEnemy(x, -20f, 0f, 0.5f * mul, 2, 2, kind, cd = 90 + Random.nextInt(60)))
            "mother" -> enemies.add(SEnemy(x, -40f, 0.4f, 0.4f, 12 + baseHp, 12 + baseHp, kind, cd = 80))
            "elite" -> enemies.add(SEnemy(x, -30f, (Random.nextFloat() - 0.5f) * 2.4f, 1.6f * mul, 3, 3, kind, cd = 120))
        }
    }
    fun spawnBoss() {
        bossActive = true; bossWarn = 90
        val hp = 30 + wave * 12
        enemies.add(SEnemy(w / 2, -50f, 1.6f, 0.6f, hp, hp, "tank", cd = 60, isBoss = true))
    }
    fun spawnDrop(x: Float, y: Float) {
        val r = Random.nextFloat()
        var k = when {
            r < 0.05f -> "bomb"
            r < 0.15f -> "life"
            r < 0.35f -> "shield"
            r < 0.55f -> "wpn"
            r < 0.70f -> "dmg"
            r < 0.82f -> "slow"
            else -> "energy"
        }
        if (settings.skin == "healer" && Random.nextFloat() < 0.5f && k != "life" && k != "bomb") k = "life"
        drops.add(SDrop(x, y, k))
    }

    fun takeHit(severity: Int = 1) {
        if (settings.assistInvuln) return
        if (iframes > 0) return
        if (System.currentTimeMillis() < shieldUntil) return
        lives -= severity
        iframes = 30
        combo = 1
        if (lives <= 0) {
            alive = false
            // adapt MMR
            settings = settings.copy(
                mmr = if (wave >= 5) min(95, settings.mmr + 4) else if (wave <= 2) max(15, settings.mmr - 3) else settings.mmr,
                totalKills = settings.totalKills + totalKills,
                coins = settings.coins + max(1, score / 5),
            )
            // unlocks
            val nu = settings.unlocks.toMutableList()
            if (wave >= 6 && "brawler" !in nu) nu.add("brawler")
            if (settings.totalKills + totalKills >= 200 && "stealth" !in nu) nu.add("stealth")
            settings = settings.copy(unlocks = nu)
            persist()
            onGameOver()
        }
    }

    if (loaded) {
        LaunchedEffect(w, h, loaded) {
            if (w > 0 && h > 0 && stars.isEmpty()) reset()
        }
        LaunchedEffect(paused, alive, w, h, perkChoice, settingsOpen) {
            if (w == 0f || !alive || paused || perkChoice != null || settingsOpen) return@LaunchedEffect
            val frameMs = if (settings.batterySaver) 33L else 16L
            while (alive && !paused && perkChoice == null && !settingsOpen) {
                delay(frameMs)
                if (System.currentTimeMillis() < slowMoUntil) slowMul = 0.5f else slowMul = 1f
                if (iframes > 0) iframes -= 1
                if (bossWarn > 0) bossWarn -= 1
                val now = System.currentTimeMillis()
                energy = min(100f, energy + (if (activePerk == "overcharge") 0.45f else 0.30f))
                // movement
                pointerX?.let { tx ->
                    val target = if (settings.oneHanded && (pointerY ?: 0f) < h * 0.5f) ship.x else tx
                    ship.x += (target - ship.x) * settings.sensitivity
                    ship.x = ship.x.coerceIn(20f, w - 20f)
                }
                if (settings.autoFire || settings.assistAutoFire) {
                    fireTimer += frameMs.toInt()
                    if (fireTimer >= shotInterval()) { fireTimer = 0; shoot() }
                }
                // bullets
                for (b in bullets) {
                    if (b.homing && b.from == 0 && enemies.isNotEmpty()) {
                        var bd = Float.MAX_VALUE; var bi = -1
                        for (i in enemies.indices) { val e = enemies[i]; val d = hypot(e.x - b.x, e.y - b.y); if (d < bd) { bd = d; bi = i } }
                        if (bi >= 0) {
                            val tgt = enemies[bi]; val dx = tgt.x - b.x; val dy = tgt.y - b.y; val m = hypot(dx, dy).coerceAtLeast(1f)
                            b.vx += dx / m * 0.4f; b.vy += dy / m * 0.4f
                            val s = hypot(b.vx, b.vy).coerceAtLeast(1f); b.vx = b.vx / s * 6f; b.vy = b.vy / s * 6f
                        }
                    }
                    b.x += b.vx * slowMul; b.y += b.vy * slowMul
                }
                bullets.removeAll { it.y < -20 || it.y > h + 20 || it.x < -20 || it.x > w + 20 }
                // drops + magnetic
                val mag = if (activePerk == "magnet") 100f else 50f
                for (d in drops) {
                    val dx = ship.x - d.x; val dy = ship.y - d.y; val dist = hypot(dx, dy)
                    if (dist < mag) { d.x += dx / dist * 2.5f; d.y += dy / dist * 2.5f }
                    d.y += d.vy
                }
                drops.removeAll { it.y > h + 30 }
                val dropIt = drops.iterator()
                while (dropIt.hasNext()) {
                    val d = dropIt.next()
                    if (abs(d.x - ship.x) < 18 && abs(d.y - ship.y) < 18) {
                        when (d.kind) {
                            "wpn" -> weaponLevel = min(5, weaponLevel + 1)
                            "shield" -> shieldUntil = now + 6000
                            "life" -> lives = min(ship.hp + 2, lives + 1)
                            "dmg" -> damageBoostUntil = now + 8000
                            "slow" -> slowMoUntil = now + 5000
                            "energy" -> energy = min(100f, energy + 35f)
                            "bomb" -> { for (e in enemies) if (!e.isBoss) { e.hp = 0; explode(e.x, e.y, Color(0xFFFACC15), 8) } }
                        }
                        dropIt.remove()
                    }
                }
                // spawn
                spawnTimer += 1
                val spawnInt = max(18, 70 - wave * 4 - (settings.mmr - 50) / 5)
                if (!bossActive && spawnTimer >= spawnInt) {
                    spawnTimer = 0; spawnEnemy()
                    if (wave >= 4 && Random.nextFloat() < 0.3f) spawnEnemy()
                }
                // enemies update
                for (en in enemies) {
                    en.x += en.vx * slowMul; en.y += en.vy * slowMul
                    if (en.x < 30 || en.x > w - 30) en.vx *= -1
                    if (en.kind == "sniper" && en.y < 80) en.vy = 0f
                    en.cd -= 1
                    if (en.cd <= 0) {
                        when {
                            en.isBoss -> {
                                en.cd = 30; en.pattern += 1
                                if (en.pattern % 4 == 0) {
                                    for (i in 0 until 8) {
                                        val a = i / 8f * (PI * 2)
                                        bullets.add(SBullet(en.x, en.y, cos(a).toFloat() * 3f, sin(a).toFloat() * 3f, 1))
                                    }
                                } else {
                                    val dx = ship.x - en.x; val dy = ship.y - en.y; val m = hypot(dx, dy).coerceAtLeast(1f)
                                    bullets.add(SBullet(en.x, en.y + 12, dx / m * 4f, dy / m * 4f, 1))
                                }
                            }
                            en.kind == "tank" -> { bullets.add(SBullet(en.x, en.y + 12, 0f, 4f, 1)); en.cd = 80 }
                            en.kind == "sniper" -> {
                                val dx = ship.x - en.x; val dy = ship.y - en.y; val m = hypot(dx, dy).coerceAtLeast(1f)
                                bullets.add(SBullet(en.x, en.y + 12, dx / m * 3f, dy / m * 3f, 1, homing = true))
                                en.cd = 130
                            }
                            en.kind == "mother" -> {
                                en.cd = 70
                                for (i in -1..1) enemies.add(SEnemy(en.x + i * 12f, en.y + 14, i * 1.2f, 1.6f, 1, 1, "spawnling"))
                            }
                            en.kind == "elite" -> {
                                bullets.add(SBullet(en.x - 6, en.y + 8, -0.6f, 4f, 1)); bullets.add(SBullet(en.x + 6, en.y + 8, 0.6f, 4f, 1))
                                en.cd = 110
                            }
                        }
                    }
                    if (en.isBoss && en.y < 80) en.y = 80f
                }
                // collisions
                for (en in enemies) {
                    val hitR = if (settings.assistHitbox) 9f else 14f
                    if (en.y > h - 20 && !en.isBoss) { takeHit(); en.hp = 0; explode(en.x, en.y, Color(0xFFEF4444), 8) }
                    if (!en.isBoss && abs(en.x - ship.x) < hitR + 12 && abs(en.y - ship.y) < hitR + 12) { takeHit(); en.hp = 0; explode(en.x, en.y, Color(0xFFEF4444), 8) }
                    if (en.isBoss && abs(en.x - ship.x) < 30 && abs(en.y - ship.y) < 26) takeHit(2)
                }
                for (b in bullets) {
                    if (b.from == 1) {
                        val hitR = if (settings.assistHitbox) 7f else 12f
                        if (abs(b.x - ship.x) < hitR && abs(b.y - ship.y) < hitR) { takeHit(); b.y = h + 100 }
                    }
                }
                // bullet vs enemy
                for (b in bullets) {
                    if (b.from != 0) continue
                    for (en in enemies) {
                        val sz = if (en.isBoss) 30f else if (en.kind == "mother") 22f else 14f
                        if (abs(b.x - en.x) < sz && abs(b.y - en.y) < sz) {
                            en.hp = (en.hp - b.dmg).toInt()
                            b.y = -100f
                            shotsHit += 1
                            if (en.hp <= 0) {
                                totalKills += 1
                                combo += 1
                                bestCombo = max(bestCombo, combo)
                                lastKillT = now
                                val v = if (en.isBoss) 200 else when (en.kind) { "mother" -> 80; "tank" -> 30; "sniper" -> 35; "elite" -> 25; else -> 10 }
                                score += (v * min(combo, 10) / 1.6).toInt()
                                onScore(score)
                                explode(en.x, en.y, if (en.isBoss) Color(0xFFEC4899) else if (en.kind == "tank") Color(0xFFF97316) else Color(0xFFFACC15), if (en.isBoss) 28 else 12)
                                if (en.isBoss) {
                                    bossActive = false; wave += 1; killsThisWave = 0
                                    for (i in 0 until 4) spawnDrop(en.x + (i - 2) * 16f, en.y)
                                } else {
                                    killsThisWave += 1
                                    if (Random.nextFloat() < 0.15f) spawnDrop(en.x, en.y)
                                    if (activePerk == "lifesteal" && totalKills % 25 == 0) lives = min(ship.hp + 2, lives + 1)
                                }
                            }
                            break
                        }
                    }
                }
                enemies.removeAll { it.hp <= 0 || it.y > h + 30 }
                // wave progression
                if (!bossActive && killsThisWave >= waveTarget) {
                    if (wave % 3 == 0) spawnBoss()
                    else {
                        wave += 1; killsThisWave = 0; waveTarget = 6 + wave * 2
                        if (activePerk == "shieldStart") shieldUntil = now + 3000
                    }
                }
                // particles
                for (p in particles) { p.x += p.vx; p.y += p.vy; p.life -= 1 }
                particles.removeAll { it.life <= 0 }
                // stars
                for (s in stars) { s.y += s.speed; if (s.y > h) { s.y = -2f; s.x = Random.nextFloat() * w } }
                // combo decay
                if (now - lastKillT > 2500 && combo > 1) combo = 1
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF02030D))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> pointerX = o.x; pointerY = o.y },
                        onDragEnd = { pointerX = null; pointerY = null },
                        onDragCancel = { pointerX = null; pointerY = null },
                    ) { change, _ -> pointerX = change.position.x; pointerY = change.position.y }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (!settings.autoFire) shoot() },
                        onLongPress = { shoot(charged = true) },
                        onDoubleTap = { fireOverdrive() },
                    )
                }
        ) {
            w = size.width; h = size.height
            // stars parallax
            for (s in stars) {
                drawCircle(Color.White.copy(alpha = s.alpha), radius = s.size, center = Offset(s.x, s.y))
            }
            // shield
            if (System.currentTimeMillis() < shieldUntil) {
                drawCircle(Color(0xFF38BDF8).copy(alpha = 0.6f), radius = 30f, center = Offset(ship.x, ship.y), style = Stroke(width = 2.5f))
            }
            // ship
            val (primary, trail) = skinColors[settings.skin] ?: Pair(accent, Color.White)
            val alpha = if (iframes > 0 && (iframes / 4) % 2 == 0) 0.4f else 1f
            drawCircle(primary.copy(alpha = alpha), radius = 16f, center = Offset(ship.x, ship.y))
            drawRect(trail.copy(alpha = alpha), Offset(ship.x - 4f, ship.y + 14f), Size(8f, 8f))
            // bullets
            for (b in bullets) {
                val c = if (b.from == 0) (if (b.charged) Color(0xFFFACC15) else trail) else Color(0xFFEF4444)
                drawRect(c, Offset(b.x - 2f, b.y - 8f), Size(4f, if (b.charged) 16f else 12f))
            }
            // enemies
            for (en in enemies) {
                if (en.isBoss) {
                    drawRect(Color(0xFFEC4899), Offset(en.x - 32f, en.y - 24f), Size(64f, 48f))
                    drawCircle(Color(0xFFFDE68A), 4f, Offset(en.x, en.y + 24f))
                    drawRect(Color(0xFF475569), Offset(40f, 10f), Size(w - 80f, 8f))
                    drawRect(Color(0xFFEF4444), Offset(40f, 10f), Size((w - 80f) * (en.hp.toFloat() / en.maxHp), 8f))
                } else when (en.kind) {
                    "tank" -> drawRect(Color(0xFFF97316), Offset(en.x - 16f, en.y - 14f), Size(32f, 28f))
                    "sniper" -> drawCircle(Color(0xFFA855F7), 14f, Offset(en.x, en.y))
                    "mother" -> drawCircle(Color(0xFFDC2626), 22f, Offset(en.x, en.y))
                    "elite" -> drawRect(Color(0xFFFACC15), Offset(en.x - 10f, en.y - 10f), Size(20f, 20f))
                    "spawnling" -> drawRect(Color(0xFFFB7185), Offset(en.x - 6f, en.y - 6f), Size(12f, 12f))
                    else -> drawRect(Color(0xFFEF4444), Offset(en.x - 12f, en.y - 10f), Size(24f, 20f))
                }
                if (en.maxHp > 1 && !en.isBoss) {
                    drawRect(Color(0xFF475569), Offset(en.x - 14f, en.y - 18f), Size(28f, 3f))
                    drawRect(Color(0xFF34D399), Offset(en.x - 14f, en.y - 18f), Size(28f * (en.hp.toFloat() / en.maxHp), 3f))
                }
            }
            // drops
            for (d in drops) {
                val col = when (d.kind) {
                    "wpn" -> Color(0xFFFACC15); "shield" -> Color(0xFF38BDF8); "life" -> Color(0xFFEF4444)
                    "dmg" -> Color(0xFFFB923C); "slow" -> Color(0xFFA855F7); "energy" -> Color(0xFF10B981); else -> Color.White
                }
                drawCircle(col, 8f, Offset(d.x, d.y))
            }
            // particles
            for (p in particles) {
                val a = p.life.toFloat() / p.life0
                drawRect(p.color.copy(alpha = a), Offset(p.x, p.y), Size(p.size, p.size))
            }
            // lives
            for (i in 0 until lives) {
                drawRect(Color(0xFFEF4444), Offset(20f + i * 16f, 20f), Size(10f, 10f))
            }
            // energy bar
            drawRect(Color.White.copy(alpha = 0.2f), Offset(w - 110f, 22f), Size(90f, 8f))
            drawRect(if (energy >= 100f) Color(0xFFFACC15) else Color(0xFF10B981), Offset(w - 110f, 22f), Size(90f * (energy / 100f), 8f))
        }

        // HUD top
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Wave $wave${if (bossActive) " · BOSS" else ""}", color = Color(0xFFFDE68A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (combo > 1) Text("×$combo combo", color = Color(0xFFFACC15), fontSize = 11.sp)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { settingsOpen = true },
                contentAlignment = Alignment.Center,
            ) { Text("⚙", color = Color.White, fontSize = 18.sp) }
        }

        if (bossWarn > 0) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
                Text("⚠ BOSS APPROACHING ⚠", color = Color(0xFFFF7B7B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // perk picker
        perkChoice?.let { perks ->
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E1A)),
                    modifier = Modifier.padding(20.dp).widthIn(max = 380.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Choose a perk", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        perks.forEach { p ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1C2030))
                                    .clickable { selectPerk(p.first) }
                                    .padding(12.dp),
                            ) {
                                Column {
                                    Text(p.second, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(p.third, color = Color.White.copy(0.65f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // settings sheet
        if (settingsOpen) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { settingsOpen = false }, contentAlignment = Alignment.Center) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E1A)),
                    modifier = Modifier.padding(16.dp).widthIn(max = 420.dp).heightIn(max = 600.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("Space Hunter · Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Sensitivity ${"%.2f".format(settings.sensitivity)}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Slider(value = settings.sensitivity, onValueChange = { settings = settings.copy(sensitivity = it); persist() }, valueRange = 0.2f..1f)
                        Text("Auto fire rate ${settings.fireRate} / sec", color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Slider(value = settings.fireRate.toFloat(), onValueChange = { settings = settings.copy(fireRate = it.toInt()); persist() }, valueRange = 2f..10f, steps = 7)
                        Row { Checkbox(checked = settings.autoFire, onCheckedChange = { settings = settings.copy(autoFire = it); persist() }); Text("Auto-fire", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.haptics, onCheckedChange = { settings = settings.copy(haptics = it); persist() }); Text("Haptics", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.reducedMotion, onCheckedChange = { settings = settings.copy(reducedMotion = it); persist() }); Text("Reduced motion", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.highContrast, onCheckedChange = { settings = settings.copy(highContrast = it); persist() }); Text("High contrast", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.batterySaver, onCheckedChange = { settings = settings.copy(batterySaver = it); persist() }); Text("Battery saver", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.oneHanded, onCheckedChange = { settings = settings.copy(oneHanded = it); persist() }); Text("One-handed", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.assistHitbox, onCheckedChange = { settings = settings.copy(assistHitbox = it); persist() }); Text("Assist: small hitbox", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.assistAutoFire, onCheckedChange = { settings = settings.copy(assistAutoFire = it); persist() }); Text("Assist: continuous fire", color = Color.White, fontSize = 12.sp) }
                        Row { Checkbox(checked = settings.assistInvuln, onCheckedChange = { settings = settings.copy(assistInvuln = it); persist() }); Text("Assist: invincibility (story)", color = Color.White, fontSize = 12.sp) }
                        Spacer(Modifier.height(8.dp))
                        Text("Hull: ${settings.skin}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (sk in listOf("interceptor","brawler","stealth","healer")) {
                                val locked = sk !in settings.unlocks
                                AssistChip(
                                    onClick = { if (!locked) { settings = settings.copy(skin = sk); persist() } },
                                    label = { Text(if (locked) "🔒 $sk" else sk, fontSize = 10.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.skin == sk) accent.copy(0.4f) else Color(0xFF1C2030)),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Adaptive difficulty: ${settings.mmr}", color = Color.White.copy(0.6f), fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { settingsOpen = false }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Close") }
                    }
                }
            }
        }
    }
}
