package be.orbinson.felix.bundle.core

import java.io.BufferedReader

/**
 * Parses the `// <original line>...` markers that Fernflower writes when decompiling with
 * [DECOMPILER_OPTIONS], so a debugger can translate between bytecode and decompiled source lines.
 */
object DecompiledLineMapping {

    private val MARKER = Regex("""//((?:\s+\d+)+)\s*$""")

    /** True when the source was produced by [SourcesUtil.decompile] with line markers. */
    fun isDecompiled(firstLine: String?): Boolean = firstLine?.trim() == DECOMPILED_BANNER

    /**
     * Returns `[original1, decompiled1, original2, decompiled2, ...]` with 1-based line numbers,
     * the layout expected by IntelliJ's `LineNumbersMapping.ArrayBasedMapping`.
     * Returns an empty array when the source carries no banner or no markers.
     */
    fun parse(reader: BufferedReader): IntArray {
        val result = ArrayList<Int>()
        var lineNumber = 0
        var first = true
        reader.lineSequence().forEach { line ->
            lineNumber++
            if (first) {
                first = false
                if (!isDecompiled(line)) return IntArray(0)
            }
            originalLines(line).forEach {
                result.add(it)
                result.add(lineNumber)
            }
        }
        return result.toIntArray()
    }

    /**
     * Moves each marked statement onto its original line number, so debuggers can use the bytecode
     * lines as-is. Only the unmarked lines since the previous statement are adjusted:
     * - too early: blank lines are inserted at the last blank separator (so gaps fall between
     *   members), or right before the statement;
     * - too late: blank lines are dropped first, then structural lines such as `}` are joined onto
     *   the line above, but only when that lands the statement exactly.
     * Statements the decompiler emits out of source order (mostly inner classes) can't be fixed this
     * way and keep relying on the markers. Text blocks and lines with other comments are left intact.
     */
    fun realign(lines: List<String>): List<String> {
        val out = ArrayList<OutLine>(lines.size)
        var pendingStart = 0
        var inTextBlock = false
        for (line in lines) {
            val togglesTextBlock = TEXT_BLOCK_DELIMITER.findAll(line).count() % 2 == 1
            val frozen = inTextBlock || togglesTextBlock
            if (togglesTextBlock) inTextBlock = !inTextBlock
            val target = if (inTextBlock) null else originalLines(line).minOrNull()
            if (target == null) {
                out.add(OutLine(line, frozen))
                continue
            }
            val current = out.size + 1
            if (target > current) pad(out, pendingStart, target - current)
            if (target < current) compact(out, pendingStart, current - target)
            out.add(OutLine(line, frozen))
            pendingStart = out.size
        }
        return out.map { it.text }
    }

    private class OutLine(var text: String, val frozen: Boolean)

    private val TEXT_BLOCK_DELIMITER = Regex("\"\"\"")

    private fun pad(out: MutableList<OutLine>, from: Int, count: Int) {
        val separator = (out.size - 1 downTo from).firstOrNull { !out[it].frozen && out[it].text.isBlank() }
        out.addAll(separator ?: out.size, List(count) { OutLine("", false) })
    }

    private fun compact(out: MutableList<OutLine>, from: Int, need: Int) {
        var remaining = need
        for (i in out.size - 1 downTo from) {
            if (remaining == 0) return
            if (!out[i].frozen && out[i].text.isBlank()) {
                out.removeAt(i)
                remaining--
            }
        }
        val joinable = (from until out.size).filter { canJoin(out, it) }
        if (joinable.size < remaining) return
        for (i in joinable.asReversed()) {
            if (remaining == 0) return
            if (!canJoin(out, i)) continue
            out[i - 1].text = join(out[i - 1].text, out[i].text)
            out.removeAt(i)
            remaining--
        }
    }

    private fun canJoin(out: List<OutLine>, i: Int): Boolean {
        if (i == 0 || out[i].frozen || out[i - 1].frozen) return false
        val previous = out[i - 1].text
        val line = out[i].text
        return previous.isNotBlank() && line.isNotBlank() && !previous.trimStart().startsWith("@") &&
                !hasOtherComment(previous) && !hasOtherComment(line)
    }

    // Conservative: also rejects "//" inside string literals, which only means a missed join.
    private fun hasOtherComment(line: String): Boolean {
        val code = MARKER.replace(line, "")
        return "//" in code || "/*" in code || "*/" in code
    }

    /** Appends [next] to [previous], keeping the marker of [previous] at the end of the line. */
    private fun join(previous: String, next: String): String {
        val marker = MARKER.find(previous) ?: return previous.trimEnd() + " " + next.trim()
        return previous.substring(0, marker.range.first).trimEnd() + " " + next.trim() +
                previous.substring(marker.range.first)
    }

    private fun originalLines(line: String): List<Int> {
        val match = MARKER.find(line) ?: return emptyList()
        return match.groupValues[1].trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    }
}
