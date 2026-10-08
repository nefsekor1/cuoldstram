// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer


class FilmMakinesi : MainAPI() {
    override var mainUrl              = "https://filmmakinesi.to"
    override var name                 = "FilmMakinesi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage            = true // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/filmler-1/sayfa/"                                to "Son Filmler",
        "${mainUrl}/film-izle/olmeden-izlenmesi-gerekenler-fm1/sayfa/" to "Ölmeden İzle",
        "${mainUrl}/tur/aksiyon-fm1/film/sayfa/"                       to "Aksiyon",
        "${mainUrl}/tur/bilim-kurgu-fm2/film/sayfa/"                   to "Bilim Kurgu",
        "${mainUrl}/tur/macera-fm1/film/sayfa/"                        to "Macera",
        "${mainUrl}/tur/komedi-fm1/film/sayfa/"                        to "Komedi",
        "${mainUrl}/tur/romantik-fm1/film/sayfa/"                      to "Romantik",
        "${mainUrl}/tur/belgesel/film/sayfa/"                      to "Belgesel",
        "${mainUrl}/tur/fantastik-fm1/film/sayfa/"                     to "Fantastik",
        "${mainUrl}/tur/polisiye/film/sayfa/"                      to "Polisiye Suç",
        "${mainUrl}/tur/korku-fm1/film/sayfa/"                         to "Korku",
        // "${mainUrl}/tur/savas/film/sayfa/"                      to "Tarihi ve Savaş",
        // "${mainUrl}/film-izle/gerilim-filmleri-izle/sayfa/"     to "Gerilim Heyecan",
        // "${mainUrl}/film-izle/gizemli/sayfa/"                   to "Gizem",
        // "${mainUrl}/film-izle/aile-filmleri/sayfa/"             to "Aile",
        // "${mainUrl}/film-izle/animasyon-filmler/sayfa/"         to "Animasyon",
        // "${mainUrl}/film-izle/western/sayfa/"                   to "Western",
        // "${mainUrl}/film-izle/biyografi/sayfa/"                 to "Biyografik",
        // "${mainUrl}/film-izle/dram/sayfa/"                      to "Dram",
        // "${mainUrl}/film-izle/muzik/sayfa/"                     to "Müzik",
        // "${mainUrl}/film-izle/spor/sayfa/"                      to "Spor"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
val cleanedUrl = request.data.removeSuffix("/")
val url = if (page > 1) {
    "$cleanedUrl/$page"
} else {
    cleanedUrl.replace(Regex("/sayfa/?$"), "")
}

val document = app.get(url, headers = mapOf(
    "User-Agent" to USER_AGENT,
    "Referer" to mainUrl
)).document

    val home = document.select("div.film-list div.item-relative")
        .mapNotNull { it.toSearchResult() }

    Log.d("FLMM", "Toplam film: ${home.size}")
    return newHomePageResponse(request.name, home)
}

private fun Element.toSearchResult(): SearchResponse? {
    val aTag = selectFirst("a.item") ?: return null
    val title = aTag.attr("data-title").takeIf { it.isNotBlank() } ?: return null
    val href = fixUrlNull(aTag.attr("href")) ?: return null
    val posterUrl = fixUrlNull(aTag.selectFirst("img")?.attr("src"))

    Log.d("FLMM", "Film: $title, Href: $href, Poster: $posterUrl")

    return newMovieSearchResponse(title, href, TvType.Movie) {
        this.posterUrl = posterUrl
    }
}
    private fun Element.toRecommendResult(): SearchResponse? {
        val title     = this.select("a").last()?.text() ?: return null
        val href      = fixUrlNull(this.select("a").last()?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/?s=${query}").document

        return document.select("div.film-list div.item-relative").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description     = document.select("div.info-description p").last()?.text()?.trim()
        val tags            = document.selectFirst("dt:contains(Tür:) + dd")?.text()?.split(", ")
        val year            = document.selectFirst("dt:contains(Yapım Yılı:) + dd")?.text()?.trim()?.toIntOrNull()

        val durationElement = document.select("dt:contains(Film Süresi:) + dd time").attr("datetime")
        // ? ISO 8601 süre formatını ayrıştırma (örneğin "PT129M")
        val duration        = if (durationElement.startsWith("PT") && durationElement.endsWith("M")) {
            durationElement.drop(2).dropLast(1).toIntOrNull() ?: 0
        } else {
            0
        }

        val recommendations = document.select("div.film-list div.item-relative").mapNotNull { it.toRecommendResult() }
        val actors          = document.selectFirst("dt:contains(Oyuncular:) + dd")?.text()?.split(", ")?.map {
            Actor(it.trim())
        }

        val trailer = document.selectFirst("div.left a.trailer-button")?.attr("data-video_url")?.substringAfter("embed/", "")?.let { 
    if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null 
}

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.duration        = duration
            this.recommendations = recommendations
            addActors(actors)
            addTrailer(trailer)
        }
    }


    private fun decodeRapidrameStream(arr: MutableList<String>): String? {
        try {
            val fd6 = arr.size - 2
            val se8 = fd6 % 7
            val u41hm = 8 + (fd6 % 5)

            val o3o95 = arr.removeAt(u41hm)
            val il4 = arr.removeAt(se8)

            var bb21r = arr.joinToString("")
            if (il4.length > 4096) {
                bb21r = String(Base64.decode(bb21r, Base64.DEFAULT), Charsets.ISO_8859_1)
            }

            var kugz3 = 0
            var l33 = 0
            for (dx09 in il4.indices) {
                val l1n = il4[dx09].code
                kugz3 = (kugz3 * 37 + l1n) % 241
                l33 = (l33 + ((l1n shl 1) xor dx09)) and 255
            }

            val up0 = (kugz3 * 3 + l33) % 256
            val p81 = (l33 % 11) + 5
            var g8doj = ((l33 * 251 + kugz3) % 65519) + 1

            for (dx09 in o3o95.length - 1 downTo 0) {
                val vz8 = o3o95[dx09]
                if (vz8 == '7') {
                    bb21r = String(Base64.decode(bb21r, Base64.DEFAULT), Charsets.ISO_8859_1)
                } else if (vz8 == '3') {
                    bb21r = bb21r.reversed()
                } else {
                    val r8yl = (26 - ((vz8.code - 96) % 26)) % 26
                    val sb = StringBuilder()
                    for (ch in bb21r) {
                        if (ch in 'a'..'z' || ch in 'A'..'Z') {
                            val ju22 = ch.code
                            val j42uf = if (ju22 <= 90) 65 else 97
                            sb.append(((ju22 - j42uf + r8yl) % 26 + j42uf).toChar())
                        } else {
                            sb.append(ch)
                        }
                    }
                    bb21r = sb.toString()
                }
            }

            if (o3o95.length > 2048) {
                bb21r = bb21r.reversed()
            }

            val len = bb21r.length
            val l1b = IntArray(len)
            for (dx09 in len - 1 downTo 1) {
                g8doj = (g8doj * 97 + 41) % 65519
                l1b[dx09] = g8doj % (dx09 + 1)
            }

            val e7j = bb21r.toCharArray()
            for (dx09 in 1 until len) {
                val oi4 = l1b[dx09]
                val hh1b = e7j[dx09]
                e7j[dx09] = e7j[oi4]
                e7j[oi4] = hh1b
            }
            bb21r = String(e7j)

            var o8br = up0
            val sb = StringBuilder()
            for (dx09 in bb21r.indices) {
                val l1n = bb21r[dx09].code
                o8br = (o8br * 5 + p81) % 256
                sb.append((l1n xor o8br).toChar())
                o8br = (o8br + l1n) % 256
            }

            val result = sb.toString()
            return if (result.startsWith("http")) result else null
        } catch (e: Exception) {
            Log.e("FLMM", "decodeRapidrameStream error: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FLMM", "data » $data")
        val document = app.get(data).document

        val iframes = document.select("iframe").mapNotNull {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            src.takeIf { s -> s.isNotBlank() }
        }
        val videoParts = document.select(".video-parts a[data-video_url]").mapNotNull {
            it.attr("data-video_url").takeIf { s -> s.isNotBlank() }
        }

        val allUrls = (iframes + videoParts).distinct().filter { !it.contains("youtube") }.map { fixUrl(it) }
        var linkFound = false

        allUrls.forEach { url ->
            Log.d("FLMM", "Processing URL: $url")
            try {
                if (url.contains("closeload") || url.contains("rapid")) {
                    val resp = app.get(url, headers = mapOf("Referer" to "${mainUrl}/", "User-Agent" to USER_AGENT)).text
                    val unpacked = try { getAndUnpack(resp) } catch (e: Exception) { resp }
                    val match = Regex("""["']([^"']{40,})["']\.split\(\s*["']([^"'])["']\s*\)""").find(unpacked)
                        ?: Regex("""["']([^"']{40,})["']\.split\(\s*["']([^"'])["']\s*\)""").find(resp)
                    if (match != null) {
                        val rawStr = match.groupValues[1].replace("\\/", "/")
                        val delim = match.groupValues[2]
                        val streamUrl = decodeRapidrameStream(rawStr.split(delim).toMutableList())
                        if (streamUrl != null && streamUrl.startsWith("http")) {
                            val sourceName = if (url.contains("closeload")) "Closeload" else "Rapidrame"
                            callback.invoke(
                                newExtractorLink(
                                    source = "${this.name} - $sourceName",
                                    name = "${this.name} - $sourceName",
                                    url = streamUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.headers = mapOf("Referer" to url, "User-Agent" to USER_AGENT)
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            linkFound = true
                        }
                    }
                } else {
                    loadExtractor(url, "${mainUrl}/", subtitleCallback) { link ->
                        linkFound = true
                        callback(link)
                    }
                }
            } catch (e: Exception) {
                Log.e("FLMM", "Error extracting $url: ${e.message}")
            }
        }
        return linkFound
    }
}
