// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink
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

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/load/page/sayfano/home/"                                       to "Yeni Eklenen Filmler",
        "${mainUrl}/load/page/sayfano/categories/nette-ilk-filmler/"               to "Nette İlk Filmler",
        "${mainUrl}/load/page/sayfano/home-series/"                                to "Yeni Eklenen Diziler",
        "${mainUrl}/load/page/sayfano/categories/tavsiye-filmler-izle2/"           to "Tavsiye Filmler",
        "${mainUrl}/load/page/sayfano/imdb7/"                                      to "IMDB 7+ Filmler",
        "${mainUrl}/load/page/sayfano/mostCommented/"                              to "En Çok Yorumlananlar",
        "${mainUrl}/load/page/sayfano/mostLiked/"                                  to "En Çok Beğenilenler",
        "${mainUrl}/load/page/sayfano/genres/aile-filmleri-izleyin-6/"             to "Aile Filmleri",
        "${mainUrl}/load/page/sayfano/genres/aksiyon-filmleri-izleyin-5/"          to "Aksiyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/animasyon-filmlerini-izleyin-5/"      to "Animasyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/belgesel-filmlerini-izle-1/"          to "Belgesel Filmleri",
        "${mainUrl}/load/page/sayfano/genres/bilim-kurgu-filmlerini-izleyin-3/"    to "Bilim Kurgu Filmleri",
        "${mainUrl}/load/page/sayfano/genres/komedi-filmlerini-izleyin-1/"         to "Komedi Filmleri",
        "${mainUrl}/load/page/sayfano/genres/korku-filmlerini-izle-4/"             to "Korku Filmleri",
        "${mainUrl}/load/page/sayfano/genres/romantik-filmleri-izle-2/"            to "Romantik Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val url = request.data.replace("sayfano", page.toString())
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "*/*", "X-Requested-With" to "fetch"
        )
        val doc = app.get(url, headers = headers, referer = mainUrl, interceptor = interceptor)
        val home: List<SearchResponse>?
        if (!doc.toString().contains("Sayfa Bulunamadı")) {
            val aa: HDFC = objectMapper.readValue(doc.toString())
            val document = Jsoup.parse(aa.html)

            home = document.select("a").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(request.name, home)
        }
        return newHomePageResponse(request.name, emptyList())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response      = app.get(
            "${mainUrl}/search?q=${query}",
            headers = mapOf("X-Requested-With" to "fetch")
        ).parsedSafe<Results>() ?: return emptyList()
        val searchResults = mutableListOf<SearchResponse>()

        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml)

            val title     = document.selectFirst("h4.title")?.text() ?: return@forEach
            val href      = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(document.selectFirst("img")?.attr("data-src"))

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl?.replace("/thumb/", "/list/") }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src"))
        val tags        = document.select("div.post-info-genres a").map { it.text() }
        val year        = document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val tvType      = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val actors      = document.select("div.post-info-cast a").map {
            Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
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
            val trailer  = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val epName    = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
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
                addTrailer(trailer)
            }
        } else {
            val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")?.substringAfter("trailer/", "")?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)

        val hdchLink    = if (decodedTwice.contains("+")) {
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
                bb21r = String(android.util.Base64.decode(bb21r, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
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
                    bb21r = String(android.util.Base64.decode(bb21r, android.util.Base64.DEFAULT), Charsets.ISO_8859_1)
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
        val pageText = app.get(url, referer = "${mainUrl}/", interceptor = interceptor).text
        val script = Jsoup.parse(pageText).select("script").find { it.data().contains("sources:") || it.data().contains("jwplayer") }?.data() ?: pageText
        Log.d("HDCH", "script » $script")
        val unpacked = try { getAndUnpack(script) } catch (e: Exception) { script }
        
        var lastUrl: String? = null
        val universalMatch = Regex("""["']([^"']{40,})["']\.split\(\s*["']([^"'])["']\s*\)""").find(unpacked)
            ?: Regex("""["']([^"']{40,})["']\.split\(\s*["']([^"'])["']\s*\)""").find(pageText)

        if (universalMatch != null) {
            val rawStr = universalMatch.groupValues[1].replace("\\/", "/")
            val delim = universalMatch.groupValues[2]
            lastUrl = decodeRapidrameStream(rawStr.split(delim).toMutableList())
        } else if (unpacked.contains("dc_hello(")) {
            val videoData = unpacked.substringAfter("file_link=\"").substringBefore("\";")
            val base64Input = videoData.substringAfter("dc_hello(\"").substringBefore("\");")
            lastUrl = dcHello(base64Input).substringAfter("https").let { "https$it" }
        }

        if (lastUrl == null || !lastUrl.startsWith("http")) return
        val subData = (if (unpacked.contains("tracks: [")) unpacked else script).substringAfter("tracks: [").substringBefore("]")
		Log.d("HDCH", "subData » $subData")
        AppUtils.tryParseJson<List<SubSource>>("[${subData}]")?.filter { it.kind == "captions"}?.map {
            val subtitleUrl = "${mainUrl}${it.file}/"

	    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
        "Referer" to "subtitleUrl"
    )
    val subtitleResponse = app.get(subtitleUrl, headers = headers, allowRedirects=true, interceptor = interceptor)
                if (subtitleResponse.isSuccessful) {
                    subtitleCallback(SubtitleFile(it.language.toString(), subtitleUrl))
                    Log.d("HDCH", "Subtitle added: $subtitleUrl")
                } else {
                    Log.d("HDCH", "Subtitle URL inaccessible: ${subtitleResponse.code}")
                }
        }
        callback.invoke(
            newExtractorLink(
                source  = source,
                name    = source,
                url     = lastUrl,
                type    = ExtractorLinkType.M3U8
			) {
                headers = mapOf("Referer" to url, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
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
    val document = app.get(data, interceptor = interceptor).document

    document.select("div.alternative-links").map { element ->
        element to element.attr("data-lang").uppercase()
    }.forEach { (element, langCode) ->
        element.select("button.alternative-link").map { button ->
            button.text().replace("(HDrip Xbet)", "").trim() + " $langCode" to button.attr("data-video")
        }.forEach { (source, videoID) ->
            val apiGet = app.get(
                "${mainUrl}/video/$videoID/", interceptor = interceptor,
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-Requested-With" to "fetch"
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
            invokeLocalSource(source, iframe, subtitleCallback, callback)
        }
    }
    return true
}
    private data class SubSource(
        @JsonProperty("file")    val file: String?  = null,
        @JsonProperty("label")   val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind")    val kind: String?  = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )
    data class HDFC(
        @JsonProperty("html") val html: String,
        @JsonProperty("meta") val meta: Meta
    )

    data class Meta(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("canonical") val canonical: Any? = null,
        @JsonProperty("keywords") val keywords: Any? = null
    )
}
