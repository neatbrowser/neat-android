package io.github.neatbrowser.search.core

/**
 * A tiny text format for the files the app keeps: one row per line, fields separated by a tab, with
 * backslash escapes for backslash, tab, line feed and carriage return. A row has at least one
 * non-empty field (blank lines are skipped when reading).
 */
object Records {
    fun encode(rows: List<List<String>>): String = buildString {
        for (row in rows) {
            row.forEachIndexed { i, field ->
                if (i > 0) append('\t')
                escapeInto(field)
            }
            append('\n')
        }
    }

    fun decode(text: String): List<List<String>> =
        text.split('\n')
            .map { it.removeSuffix("\r") }
            .filter { it.isNotEmpty() }
            .map { line -> line.split('\t').map(::unescape) }

    private fun StringBuilder.escapeInto(field: String) {
        for (c in field) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    private fun unescape(field: String): String {
        if (!field.contains('\\')) return field
        val out = StringBuilder(field.length)
        var i = 0
        while (i < field.length) {
            val c = field[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            if (i + 1 >= field.length) break // lone trailing backslash
            when (val next = field[i + 1]) {
                't' -> out.append('\t')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}
