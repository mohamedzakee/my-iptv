package com.zakee.zedtv

/** Parses an M3U playlist. Streams with the same tvg-id become one channel with backup streams. */
object M3u {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val qualityRegex = Regex("""\s*[(\[].*?[)\]]""")

    private class Builder(val id: String, val name: String, val group: String, val logo: String?) {
        val streams = mutableListOf<Stream>()
    }

    fun parse(text: String): List<Channel> {
        val channels = LinkedHashMap<String, Builder>()
        var info: String? = null
        var ua: String? = null
        var ref: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> { info = line; ua = null; ref = null }
                line.startsWith("#EXTVLCOPT:http-user-agent=") -> ua = line.substringAfter("=")
                line.startsWith("#EXTVLCOPT:http-referrer=") -> ref = line.substringAfter("=")
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val inf = info ?: continue
                    val attrs = attrRegex.findAll(inf).associate { it.groupValues[1] to it.groupValues[2] }
                    val title = if (inf.contains("\",")) inf.substringAfterLast("\",") else inf.substringAfterLast(",")
                    val name = title.replace(qualityRegex, "").trim()
                    val id = attrs["tvg-id"]?.takeIf { it.isNotBlank() }?.substringBefore("@") ?: name
                    val b = channels.getOrPut(id) {
                        Builder(id, name, attrs["group-title"] ?: "Other", attrs["tvg-logo"]?.takeIf { it.isNotBlank() })
                    }
                    b.streams.add(Stream(line, ua, ref))
                    info = null
                }
            }
        }
        return channels.values.mapIndexed { i, b -> Channel(b.id, i + 1, b.name, b.group, b.logo, b.streams.toList()) }
    }
}
