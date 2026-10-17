// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class HDFilmCehennemi : MainAPI() {
    override var mainUrl              = "https://www.hdfilmcehennemi.nl"
    override var name                 = "HDFilmCehennemi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 150L
    override var sequentialMainPageScrollDelay = 150L

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    // ! CloudFlare bypass
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)

            if (response.code in listOf(403, 503)) {
                response.close()
                return try {
                    cloudflareKiller.intercept(chain)
                } catch (e: Throwable) {
                    chain.proceed(request)
                }
            }

            val body = try {
                response.peekBody(1024 * 1024).string()
            } catch (e: Throwable) {
                ""
            }

            if (body.contains("Just a moment") || body.contains("Checking your browser") || body.contains("cf-browser-verification") || body.contains("Attention Required")) {
                response.close()
                return try {
                    cloudflareKiller.intercept(chain)
                } catch (e: Throwable) {
                    chain.proceed(request)
                }
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        mainUrl                                           to "Yeni Eklenen Filmler",
        "${mainUrl}/yabancidiziizle-2"                    to "Yeni Eklenen Diziler",
        "${mainUrl}/category/tavsiye-filmler-izle2"       to "Tavsiye Filmler",
        "${mainUrl}/imdb-7-puan-uzeri-filmler"            to "IMDB 7+ Filmler",
        "${mainUrl}/en-cok-yorumlananlar-1"               to "En Çok Yorumlananlar",
        "${mainUrl}/en-cok-begenilen-filmleri-izle"       to "En Çok Beğenilenler",
        "${mainUrl}/tur/aile-filmleri-izleyin-6"          to "Aile Filmleri",
        "${mainUrl}/tur/aksiyon-filmleri-izleyin-3"       to "Aksiyon Filmleri",
        "${mainUrl}/tur/animasyon-filmlerini-izleyin-4"   to "Animasyon Filmleri",
        "${mainUrl}/tur/belgesel-filmlerini-izle-1"       to "Belgesel Filmleri",
        "${mainUrl}/tur/bilim-kurgu-filmlerini-izleyin-2" to "Bilim Kurgu Filmleri",
        "${mainUrl}/tur/komedi-filmlerini-izleyin-1"      to "Komedi Filmleri",
        "${mainUrl}/tur/korku-filmlerini-izle-2"          to "Korku Filmleri",
        "${mainUrl}/tur/romantik-filmleri-izle-1"         to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data.removeSuffix("/")}/page/$page/"
        } else {
            request.data
        }

        val document = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/"
                ),
                interceptor = interceptor
            ).document
        } catch (e: Throwable) {
            Log.e("HDCH", "getMainPage error: ${e.message}")
            return newHomePageResponse(request.name, emptyList())
        }

        val home = document.select("div.section-content a.poster, div.card-body a.poster, a.poster, div.film-list div.item-relative")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("strong.poster-title, h4.title, h2.title")?.text()?.trim()
            ?: this.attr("title").takeIf { it.isNotBlank() }?.trim()
            ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
            ?: fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()

        try {
            val response = app.get(
                "${mainUrl}/search?q=${query}",
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "${mainUrl}/",
                    "X-Requested-With" to "fetch"
                ),
                interceptor = interceptor
            ).parsedSafe<Results>()

            response?.results?.forEach { resultHtml ->
                val document = Jsoup.parse(resultHtml)
                val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
                val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
                val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src"))
                    ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

                searchResults.add(
                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl?.replace("/thumb/", "/list/")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("HDCH", "Search JSON error: ${e.message}")
        }

        if (searchResults.isEmpty()) {
            try {
                val doc = app.get(
                    "${mainUrl}/arama/?q=${query}",
                    headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "${mainUrl}/"
                    ),
                    interceptor = interceptor
                ).document

                doc.select("div.section-content a.poster, a.poster").forEach { elem ->
                    elem.toSearchResult()?.let { searchResults.add(it) }
                }
            } catch (e: Exception) {
                Log.e("HDCH", "Search HTML error: ${e.message}")
            }
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "${mainUrl}/"
                ),
                interceptor = interceptor
            ).document
        } catch (e: Throwable) {
            Log.e("HDCH", "load error: ${e.message}")
            return null
        }

        val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle")?.trim()
            ?: document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
            ?: fixUrlNull(document.selectFirst("aside.post-info-poster img")?.attr("src"))
            ?: fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
        val tags        = document.select("div.post-info-genres a").map { it.text() }
        val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val tvType      = if (document.select("div.seasons").isEmpty() && document.select("div.seasons-tab-content").isEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val actors      = document.select("div.post-info-cast a").mapNotNull {
            val actorName = it.selectFirst("strong")?.text() ?: return@mapNotNull null
            Actor(actorName, it.selectFirst("img")?.attr("data-src"))
        }

        val recommendations = document.select("div.section-slider-container div.slider-slide").mapNotNull {
            val recName      = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
            val recHref      = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.seasons-tab-content a, div.seasons a").mapNotNull {
                val epName    = it.selectFirst("h4")?.text()?.trim() ?: it.text().trim().takeIf { t -> t.isNotBlank() } ?: return@mapNotNull null
                val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    private fun safeBase64Decode(str: String): String {
        return try {
            String(android.util.Base64.decode(str, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
        } catch (e: Exception) {
            try {
                String(android.util.Base64.decode(str, android.util.Base64.URL_SAFE), Charsets.ISO_8859_1)
            } catch (e2: Exception) {
                try {
                    String(android.util.Base64.decode(str, android.util.Base64.NO_PADDING), Charsets.ISO_8859_1)
                } catch (e3: Exception) {
                    str
                }
            }
        }
    }

    private fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)

        val hdchLink = if (decodedTwice.contains("+")) {
            decodedTwice.substringAfterLast("+")
        } else if (decodedTwice.contains(" ")) {
            decodedTwice.substringAfterLast(" ")
        } else if (decodedTwice.contains("|")){
            decodedTwice.substringAfterLast("|")
        } else {
            decodedTwice
        }
        Log.d("HDCH", "decodedTwice $decodedTwice")
        return hdchLink
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
                bb21r = safeBase64Decode(bb21r)
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
                    bb21r = safeBase64Decode(bb21r)
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
            Log.e("HDCH", "decodeRapidrameStream error: ${e.message}")
            return null
        }
    }

    private suspend fun invokeLocalSource(source: String, url: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit ) {
        val pageText = try {
            app.get(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "${mainUrl}/"
                ),
                interceptor = interceptor
            ).text
        } catch (e: Throwable) {
            Log.e("HDCH", "invokeLocalSource error: ${e.message}")
            return
        }

        val scripts = Jsoup.parse(pageText).select("script").map { it.data() }
        val candidateTexts = mutableListOf<String>()

        for (s in scripts) {
            if (s.contains("eval(function(p,a,c,k,e,d)")) {
                try {
                    val unpacked = getAndUnpack(s)
                    if (unpacked.isNotBlank()) candidateTexts.add(unpacked)
                } catch (e: Throwable) {
                    Log.e("HDCH", "Unpack error: ${e.message}")
                }
            } else if (s.contains("sources:") || s.contains(".split(")) {
                candidateTexts.add(s)
            }
        }
        candidateTexts.add(pageText)

        var lastUrl: String? = null
        val splitRegex = Regex("""["']([^"']{40,})["']\.split\(\s*["']([^"'])["']\s*\)""")

        for (text in candidateTexts) {
            val matches = splitRegex.findAll(text).filter { it.groupValues[2] != "|" }
            for (m in matches) {
                val rawStr = m.groupValues[1].replace("\\/", "/")
                val delim = m.groupValues[2]
                val decoded = decodeRapidrameStream(rawStr.split(delim).toMutableList())
                if (decoded != null && decoded.startsWith("http")) {
                    lastUrl = decoded
                    break
                }
            }
            if (lastUrl != null) break

            if (text.contains("dc_hello(")) {
                val videoData = text.substringAfter("file_link=\"").substringBefore("\";")
                val base64Input = videoData.substringAfter("dc_hello(\"").substringBefore("\");")
                val decoded = dcHello(base64Input).substringAfter("https").let { "https$it" }
                if (decoded.startsWith("http")) {
                    lastUrl = decoded
                    break
                }
            }
        }

        if (lastUrl == null || !lastUrl.startsWith("http")) {
            val nestedIframe = Jsoup.parse(pageText).select("iframe").firstOrNull()?.let {
                fixUrlNull(it.attr("data-src").takeIf { s -> s.isNotBlank() } ?: it.attr("src"))
            }
            if (nestedIframe != null && nestedIframe != url) {
                var target = nestedIframe
                if (target.contains("rapidrame_id=")) {
                    val rapidId = target.substringAfter("rapidrame_id=").substringBefore("&").removeSuffix("/")
                    target = "${mainUrl}/rplayer/$rapidId/"
                }
                loadExtractor(target, "${mainUrl}/", subtitleCallback, callback)
            }
            return
        }

        val subData = candidateTexts.find { it.contains("tracks: [") }?.substringAfter("tracks: [")?.substringBefore("]")
        if (!subData.isNullOrBlank()) {
            try {
                AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions"}?.map {
                    val subtitleUrl = "${mainUrl}${it.file}/"
                    val headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Referer" to "${mainUrl}/"
                    )
                    val subtitleResponse = app.get(subtitleUrl, headers = headers, allowRedirects=true, interceptor = interceptor)
                    if (subtitleResponse.isSuccessful) {
                        subtitleCallback(SubtitleFile(it.language.toString(), subtitleUrl))
                        Log.d("HDCH", "Subtitle added: $subtitleUrl")
                    } else {
                        Log.d("HDCH", "Subtitle URL inaccessible: ${subtitleResponse.code}")
                    }
                }
            } catch (e: Throwable) {
                Log.e("HDCH", "Subtitle parse error: ${e.message}")
            }
        }

        callback.invoke(
            newExtractorLink(
                source  = "${this.name} - $source",
                name    = "${this.name} - $source",
                url     = lastUrl,
                type    = ExtractorLinkType.M3U8
            ) {
                headers = mapOf(
                    "Referer" to url,
                    "User-Agent" to USER_AGENT
                )
                quality = Qualities.P1080.value
            }
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("HDCH", "data » $data")
        val document = try {
            app.get(
                data,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "${mainUrl}/"
                ),
                interceptor = interceptor
            ).document
        } catch (e: Throwable) {
            Log.e("HDCH", "loadLinks document error: ${e.message}")
            try {
                app.get(data, headers = mapOf("User-Agent" to USER_AGENT)).document
            } catch (e2: Throwable) {
                return false
            }
        }

        var linkFound = false
        document.select("div.alternative-links").map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            element.select("button.alternative-link").map { button ->
                button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
            }.forEach { (source, videoID) ->
                try {
                    val apiGet = app.get(
                        "${mainUrl}/video/$videoID/", interceptor = interceptor,
                        headers = mapOf(
                            "X-Requested-With" to "fetch",
                            "User-Agent" to USER_AGENT
                        ),
                        referer = data
                    ).text
                    Log.d("HDCH", "Found videoID: $videoID")
                    val rawIframe = Regex("""(?:data-src|src)=\\?["']([^"']+)""").find(apiGet)?.groupValues?.get(1)?.replace("\\", "") ?: return@forEach
                    var iframe = rawIframe
                    Log.d("HDCH", "rawIframe » $rawIframe")
                    if (iframe.contains("rapidrame_id=")) {
                        val rapidId = iframe.substringAfter("rapidrame_id=").substringBefore("&").removeSuffix("/")
                        iframe = "${mainUrl}/rplayer/$rapidId/"
                    } else if (iframe.contains("mobi")) {
                        val iframeDoc = Jsoup.parse(apiGet)
                        val mobiSrc = fixUrlNull(iframeDoc.selectFirst("iframe")?.attr("data-src")) ?: return@forEach
                        if (mobiSrc.contains("rapidrame_id=")) {
                            val rapidId = mobiSrc.substringAfter("rapidrame_id=").substringBefore("&").removeSuffix("/")
                            iframe = "${mainUrl}/rplayer/$rapidId/"
                        } else {
                            iframe = mobiSrc
                        }
                    } else if (!iframe.startsWith("http")) {
                        iframe = fixUrl(iframe)
                    }
                    Log.d("HDCH", "$source » $videoID » $iframe")
                    var currentFound = false
                    invokeLocalSource(source, iframe, subtitleCallback) { link ->
                        currentFound = true
                        linkFound = true
                        callback(link)
                    }
                    if (!currentFound) {
                        loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                            linkFound = true
                            callback(link)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("HDCH", "Error in source $source: ${e.message}")
                }
            }
        }

        if (!linkFound) {
            document.select("iframe").forEach { el ->
                try {
                    val src = fixUrlNull(el.attr("data-src").takeIf { s -> s.isNotBlank() } ?: el.attr("src")) ?: return@forEach
                    var iframe = src
                    if (iframe.contains("rapidrame_id=")) {
                        val rapidId = iframe.substringAfter("rapidrame_id=").substringBefore("&").removeSuffix("/")
                        iframe = "${mainUrl}/rplayer/$rapidId/"
                    }
                    var currentFound = false
                    invokeLocalSource("Player", iframe, subtitleCallback) { link ->
                        currentFound = true
                        linkFound = true
                        callback(link)
                    }
                    if (!currentFound) {
                        loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                            linkFound = true
                            callback(link)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("HDCH", "Direct iframe error: ${e.message}")
                }
            }
        }

        return linkFound
    }

    private data class SubSource(
        @JsonProperty("file")     val file: String?  = null,
        @JsonProperty("label")    val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind")     val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
}
