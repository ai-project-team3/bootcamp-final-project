package com.example.finalproject_demo.net

import android.content.Context
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/**
 * A line voiced in pieces, so only the part that changes is bought from `/tts` (10-14 진웅 · #461 · #502).
 *
 * Two cuts, both chosen by blind listening (1–5, 5 = cannot tell it from the whole line · `eval/splice_listen*.py`):
 * - **where a sentence ends** (「!」 「?」 「.」 then a space): 「다 만들었어! | 제목은 『…』이야.」 — 5.0 on 8 lines.
 *   A sentence baked into the app, or already voiced this session (「천천히 생각해 봐. | {the question just asked}」),
 *   costs nothing; only the other sentence goes to `/tts`.
 * - **after a heard word with its particle**: 「놀이터에서 | 무슨 일이 있었어?」 — 4.0–5.0. The word **keeps its particle**
 *   (「놀이터」 + 「에서 무슨 일이 있었어?」 was 1.5: 연음 and a sentence-final pitch on the lone word); the head is
 *   voiced once and reused for the session, the rest is baked.
 *
 * A line is spliced only when it splits **exactly** into such pieces and at least one of them is free — otherwise null,
 * and the whole line goes to `/tts` as before. The line is never parsed for meaning, and nothing a child said is cut.
 * Off with `-e splice off` (debug builds), to hear the same scene the old way.
 */
object VoiceSplice {
    @Volatile var on = true

    @Volatile private var ctx: Context? = null
    private val tails: Set<String> by lazy { ctx?.assets?.list("voice_tail")?.toSet() ?: emptySet() }

    fun attach(context: Context) { ctx = context.applicationContext }

    /** The baked sound for exactly [text] — a tail ([eval/bake_tails.py]) or a line baked whole ([Voice.baked]) */
    fun baked(text: String): ByteArray? {
        val name = Voice.bakedKey(text) + ".mp3"
        if (name in tails) return runCatching { ctx?.assets?.open("voice_tail/$name")?.use { it.readBytes() } }.getOrNull()
        return Voice.baked(text)
    }

    sealed interface Seg {
        val text: String
        /** silence before this piece; 0 = joined with a short cross-fade */
        val gapMs: Int
    }
    /** Baked — free */
    data class Tail(override val text: String, override val gapMs: Int) : Seg
    /** A heard word with its particle — `/tts` once, reused; read alone it can drag ([tooSlow]) */
    data class Head(override val text: String, override val gapMs: Int) : Seg
    /** A sentence of its own — `/tts`, or free when it was voiced already this session */
    data class Live(override val text: String, override val gapMs: Int) : Seg

    /** Between two sentences — a little under the 400 ms between two lines (Director.LINE_GAP_MS) · 10-14 round 3: 5.0 */
    const val SENTENCE_GAP_MS = 280
    /** Between a head and its tail · 10-14 round 1: 4.0 */
    const val WORD_GAP_MS = 70

    /**
     * A head read alone longer than this per syllable dragged — 10-14 「할머니 집에서」 0.38 s (rated 1) against
     * 0.18–0.30 s for the other heads (rated 4–5). Such a head is not used; the line goes to `/tts` whole.
     */
    const val MAX_HEAD_SEC_PER_SYLLABLE = 0.33

    /**
     * The sentences of [line]: cut after 「!」 「?」 「.」 followed by a space, never inside 『…』 「…」 "…" (a title or a
     * quote keeps its own marks). One sentence = the line itself.
     */
    fun sentences(line: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var quote = false
        var start = 0
        for (i in line.indices) {
            val c = line[i]
            if (c == '『' || c == '「') depth++
            else if (c == '』' || c == '」') depth = maxOf(0, depth - 1)
            else if (c == '"') quote = !quote
            else if (c in "!?." && depth == 0 && !quote && line.getOrNull(i + 1) == ' ') {
                out += line.substring(start, i + 1).trim()
                start = i + 2
            }
        }
        line.substring(minOf(start, line.length)).trim().takeIf(String::isNotEmpty)?.let { out += it }
        return out
    }

    /**
     * [line] in pieces, or null when splicing would not save anything or the line does not split exactly.
     * Each sentence is: baked ([isBaked]) → [Tail]; a heard word + particle, a space, then a baked rest → [Head] + [Tail];
     * otherwise → [Live]. Worth it only when some piece is free: a [Tail], or a [Live] already voiced ([isHeard]).
     * A [Head] counts as worth it on its own — it is bought once and reused.
     */
    fun plan(line: String, words: Collection<String>, isBaked: (String) -> Boolean, isHeard: (String) -> Boolean = { false }): List<Seg>? {
        val ss = sentences(line)
        val segs = mutableListOf<Seg>()
        ss.forEachIndexed { i, s ->
            val gap = if (i == 0) 0 else SENTENCE_GAP_MS
            when {
                isBaked(s) -> segs += Tail(s, gap)
                else -> head(s, words, isBaked)?.let { (h, t) -> segs += Head(h, gap); segs += Tail(t, WORD_GAP_MS) }
                    ?: run { segs += Live(s, gap) }
            }
        }
        val free = segs.any { it is Tail || it is Head || (it is Live && isHeard(it.text)) }
        // one sentence that is neither baked nor headed is the whole line — nothing to splice
        return if (segs.size < 2 || !free) null else segs
    }

    /** (heard word + its particle, the baked rest) when [s] starts with one of [words] — the longest word wins */
    private fun head(s: String, words: Collection<String>, isBaked: (String) -> Boolean): Pair<String, String>? {
        val w = words.map(String::trim).filter(String::isNotEmpty).sortedByDescending { it.length }
            .firstOrNull { s.startsWith(it) } ?: return null
        val space = s.indexOf(' ', w.length).takeIf { it > 0 } ?: return null
        val rest = s.substring(space + 1).trim()
        return if (rest.isNotEmpty() && isBaked(rest)) s.substring(0, space) to rest else null
    }

    /** Was this head read too slowly to join ([MAX_HEAD_SEC_PER_SYLLABLE])? */
    fun tooSlow(head: String, p: Pcm): Boolean {
        val syllables = head.count { !it.isWhitespace() }.coerceAtLeast(1)
        return trim(p).size / p.rate.toDouble() / syllables > MAX_HEAD_SEC_PER_SYLLABLE
    }

    // ── joining the sound ──────────────────────────────────────────

    /** 16-bit mono PCM and its rate */
    class Pcm(val samples: ShortArray, val rate: Int)

    /** 20 ms frames quieter than this are pauses, not voice — the same gate as `backend/app/audio_level.py` */
    private const val GATE_DBFS = -45.0

    /** Only the voice: the silence a TTS clip carries at each end is cut, 15 ms kept around */
    fun trim(p: Pcm): ShortArray {
        val frame = maxOf(1, p.rate / 50)
        val n = p.samples.size / frame
        val floor = 32768.0 * Math.pow(10.0, GATE_DBFS / 20)
        val loud = (0 until n).filter { f ->
            var sum = 0.0
            for (k in f * frame until (f + 1) * frame) { val v = p.samples[k].toDouble(); sum += v * v }
            Math.sqrt(sum / frame) > floor
        }
        if (loud.isEmpty()) return p.samples
        val pad = p.rate * 15 / 1000
        return p.samples.copyOfRange(maxOf(0, loud.first() * frame - pad), minOf(p.samples.size, (loud.last() + 1) * frame + pad))
    }

    /** The trimmed pieces end to end — silence where a gap is asked, an 8 ms cross-fade where none is */
    fun join(pieces: List<Pair<Pcm, Int>>): Pcm? {
        val rate = pieces.firstOrNull()?.first?.rate ?: return null
        if (pieces.any { it.first.rate != rate }) return null
        val fade = rate * 8 / 1000
        var out = ShortArray(0)
        for ((p, gap) in pieces) {
            val t = trim(p)
            out = when {
                out.isEmpty() -> t
                gap > 0 -> out + ShortArray(rate * gap / 1000) + t
                out.size < fade || t.size < fade -> out + t
                else -> {
                    val head = out.copyOf(out.size - fade)
                    val mix = ShortArray(fade) { k ->
                        val a = k / fade.toDouble()
                        (out[out.size - fade + k].toDouble() * (1 - a) + t[k].toDouble() * a).toInt().toShort()
                    }
                    head + mix + t.copyOfRange(fade, t.size)
                }
            }
        }
        return Pcm(out, rate)
    }

    /** mp3 → 16-bit mono PCM on the phone (MediaCodec). Null when it cannot be read — then the line goes to `/tts` whole */
    fun decode(mp3: ByteArray): Pcm? = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= mp3.size) return -1
                    val n = minOf(size.toLong(), mp3.size - position).toInt()
                    System.arraycopy(mp3, position.toInt(), buffer, offset, n)
                    return n
                }
                override fun getSize() = mp3.size.toLong()
                override fun close() {}
            })
            val track = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            val chunks = mutableListOf<ShortArray>()
            try {
                codec.configure(fmt, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inDone = false
                var outDone = false
                while (!outDone) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                            if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                            else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o >= 0) {
                        val b = codec.getOutputBuffer(o)!!
                        b.position(info.offset); b.limit(info.offset + info.size)
                        val sb = b.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        chunks += ShortArray(sb.remaining()).also { sb.get(it) }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally { runCatching { codec.stop() }; codec.release() }
            val all = ShortArray(chunks.sumOf { it.size }).also { a -> var at = 0; chunks.forEach { c -> c.copyInto(a, at); at += c.size } }
            val mono = if (channels <= 1) all else ShortArray(all.size / channels) { k ->
                (0 until channels).sumOf { c -> all[k * channels + c].toInt() }.div(channels).toShort()
            }
            Pcm(mono, rate)
        } finally { ex.release() }
    }.getOrNull()
}
