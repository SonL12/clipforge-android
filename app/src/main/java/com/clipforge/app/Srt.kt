package com.clipforge.app

data class Cue(val time: String, val text: String)

fun parseSrt(s: String): List<Cue> {
    val blocks = s.replace("\r\n", "\n").replace("\r", "\n").trim().split(Regex("\n\\s*\n"))
    return blocks.mapNotNull { b ->
        val lines = b.split("\n")
        val ti = lines.indexOfFirst { "-->" in it }
        if (ti < 0) null
        else Cue(lines[ti].trim(), lines.drop(ti + 1).joinToString("\n").trim())
    }
}

fun toSrt(cues: List<Cue>): String =
    cues.filter { it.text.isNotBlank() }
        .mapIndexed { i, c -> "${i + 1}\n${c.time}\n${c.text.trim()}\n" }
        .joinToString("\n")