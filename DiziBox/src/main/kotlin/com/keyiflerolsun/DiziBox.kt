// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLDecoder

class DiziBox : MainAPI() {
    override var mainUrl              = "https://www.dizibox.live"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

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
        "${mainUrl}/dizi-arsivi/page/SAYFA/" to "Son Eklenen Diziler",
        "${mainUrl}/tur/aile/page/SAYFA/"    to "Aile",
        "${mainUrl}/tur/aksiyon/page/SAYFA"  to "Aksiyon",
        "${mainUrl}/tur/bilimkurgu/page/SAYFA" to "Bilim Kurgu",
        "${mainUrl}/tur/komedi/page/SAYFA"   to "Komedi",
        "${mainUrl}/tur/korku/page/SAYFA"    to "Korku"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val url = request.data.replace("SAYFA", "$page")
            val document = app.get(
                url,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
                cookies = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            val home = if (request.name == "Son Eklenen Diziler") {
                document.select("article.detailed-article").mapNotNull { it.toMainPageResult() }
            } else {
                val list = document.select("article.article-series-poster").mapNotNull { it.toMainPageResult() }
                if (list.isEmpty()) {
                    document.select("article.detailed-article, article.grid-box").mapNotNull { it.toMainPageResult() }
                } else {
                    list
                }
            }
            newHomePageResponse(request.name, home)
        } catch (e: Throwable) {
            Log.e("DZBX", "getMainPage error: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val aTag = this.selectFirst("a.poster-title, h2 a, h3 a, a") ?: return null
        val title = aTag.text().trim().takeIf { it.isNotBlank() } ?: this.attr("title").takeIf { it.isNotBlank() }?.trim() ?: return null
        val href = fixUrlNull(aTag.attr("href")) ?: return null
        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.let { img ->
                img.attr("data-src").takeIf { it.isNotBlank() } ?: img.attr("src")
            }
        )

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val document = app.get(
                "${mainUrl}/?s=${query}",
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
                cookies = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            document.select("article.detailed-article, article.article-series-poster, div.film-list div.item-relative").mapNotNull { it.toMainPageResult() }
        } catch (e: Throwable) {
            Log.e("DZBX", "search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val document = app.get(
                url,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
                cookies = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            val title       = document.selectFirst("div.tv-overview h1 a, h1.entry-title, h1")?.text()?.trim() ?: return null
            val poster      = fixUrlNull(document.selectFirst("div.tv-overview figure img, div.poster img")?.attr("src"))
                ?: fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
            val description = document.selectFirst("div.tv-story p, div.entry-content p")?.text()?.trim()
            val year        = document.selectFirst("a[href*='/yil/']")?.text()?.trim()?.toIntOrNull()
            val tags        = document.select("a[href*='/tur/']").map { it.text() }
            val actors      = document.select("a[href*='/oyuncu/']").map { Actor(it.text()) }
            val trailer     = document.selectFirst("div.tv-overview iframe")?.attr("src")

            val episodeList = mutableListOf<Episode>()
            document.select("div#seasons-list a").forEach {
                val epUrl = fixUrlNull(it.attr("href")) ?: return@forEach
                try {
                    val epDoc = app.get(
                        epUrl,
                        headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
                        cookies = mapOf(
                            "LockUser"      to "true",
                            "isTrustedUser" to "true",
                            "dbxu"          to "1743289650198"
                        ),
                        interceptor = interceptor
                    ).document

                    epDoc.select("article.grid-box").forEach ep@ { epElem ->
                        val epTitle   = epElem.selectFirst("div.post-title a")?.text()?.trim() ?: return@ep
                        val epHref    = fixUrlNull(epElem.selectFirst("div.post-title a")?.attr("href")) ?: return@ep
                        val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                        val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                        episodeList.add(newEpisode(epHref) {
                            this.name = epTitle
                            this.season = epSeason
                            this.episode = epEpisode
                        })
                    }
                } catch (e: Throwable) {
                    Log.e("DZBX", "Season load error: ${e.message}")
                }
            }

            if (episodeList.isEmpty()) {
                document.select("article.grid-box").forEach ep@ { epElem ->
                    val epTitle   = epElem.selectFirst("div.post-title a")?.text()?.trim() ?: return@ep
                    val epHref    = fixUrlNull(epElem.selectFirst("div.post-title a")?.attr("href")) ?: return@ep
                    val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()

                    episodeList.add(newEpisode(epHref) {
                        this.name = epTitle
                        this.season = epSeason
                        this.episode = epEpisode
                    })
                }
            }

            if (episodeList.isEmpty()) {
                document.select("a[href*='-sezon-'][href*='-bolum']").forEach { a ->
                    val epHref = fixUrlNull(a.attr("href")) ?: return@forEach
                    val epTitle = a.text().trim().takeIf { it.isNotBlank() } ?: return@forEach
                    val epSeason  = Regex("""(\d+)\. ?Sezon""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epEpisode = Regex("""(\d+)\. ?Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                    if (epEpisode != null && episodeList.none { it.data == epHref }) {
                        episodeList.add(newEpisode(epHref) {
                            this.name = epTitle
                            this.season = epSeason
                            this.episode = epEpisode
                        })
                    }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.posterUrl = poster
                this.plot      = description
                this.year      = year
                this.tags      = tags
                addActors(actors)
                addTrailer(trailer)
            }
        } catch (e: Throwable) {
            Log.e("DZBX", "load error: ${e.message}")
            null
        }
    }

    private suspend fun iframeDecode(data: String, iframe: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            @Suppress("NAME_SHADOWING") var iframe = fixUrl(iframe)

            if (iframe.contains("/player/king/king.php")) {
                iframe = iframe.replace("king.php?v=", "king.php?wmode=opaque&v=")
                val subDoc = app.get(
                    iframe,
                    referer     = data,
                    headers     = mapOf("User-Agent" to USER_AGENT),
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1743289650198"
                    ),
                    interceptor = interceptor
                ).document
                val subFrame = fixUrlNull(subDoc.selectFirst("div#Player iframe, iframe")?.attr("src")) ?: return false

                val iDoc = app.get(subFrame, referer = "${mainUrl}/", headers = mapOf("User-Agent" to USER_AGENT)).text
                val decryptMatch = Regex("""CryptoJS\.AES\.decrypt\(\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""").find(iDoc)
                val cryptData = decryptMatch?.groupValues?.get(1)
                val cryptPass = decryptMatch?.groupValues?.get(2)

                if (cryptData != null && cryptPass != null) {
                    val decryptedData = try { CryptoJS.decrypt(cryptPass, cryptData) } catch (e: Throwable) { "" }
                    val decryptedDoc  = Jsoup.parse(decryptedData)
                    val vidUrl = Regex("""file:\s*['"]([^'"]+)['"]""").find(decryptedDoc.html())?.groupValues?.get(1)
                        ?: Regex("""file:\s*['"]([^'"]+)['"]""").find(iDoc)?.groupValues?.get(1)

                    if (vidUrl != null && vidUrl.startsWith("http")) {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = this.name,
                                url = vidUrl,
                                type = if (vidUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                headers = mapOf("Referer" to vidUrl, "User-Agent" to USER_AGENT)
                                quality = Qualities.P1080.value
                            }
                        )
                        return true
                    }
                }
                loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

            } else if (iframe.contains("/player/moly/moly.php")) {
                iframe = iframe.replace("moly.php?h=", "moly.php?wmode=opaque&h=")
                var subDoc = app.get(
                    iframe,
                    referer     = data,
                    headers     = mapOf("User-Agent" to USER_AGENT),
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1743289650198"
                    ),
                    interceptor = interceptor
                ).document

                val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
                if (atobData != null) {
                    val decodedAtob = try { URLDecoder.decode(atobData, "UTF-8") } catch (e: Throwable) { atobData }
                    val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                    subDoc          = Jsoup.parse(strAtob)
                }

                val subFrame = fixUrlNull(subDoc.selectFirst("div#Player iframe, iframe")?.attr("src")) ?: return false
                loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

            } else if (iframe.contains("/player/haydi.php")) {
                iframe = iframe.replace("haydi.php?v=", "haydi.php?wmode=opaque&v=")
                var subDoc = app.get(
                    iframe,
                    referer     = data,
                    headers     = mapOf("User-Agent" to USER_AGENT),
                    cookies     = mapOf(
                        "LockUser"      to "true",
                        "isTrustedUser" to "true",
                        "dbxu"          to "1743289650198"
                    ),
                    interceptor = interceptor
                ).document

                val atobData = Regex("""unescape\("(.*)"\)""").find(subDoc.html())?.groupValues?.get(1)
                if (atobData != null) {
                    val decodedAtob = try { URLDecoder.decode(atobData, "UTF-8") } catch (e: Throwable) { atobData }
                    val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                    subDoc          = Jsoup.parse(strAtob)
                }

                val subFrame = fixUrlNull(subDoc.selectFirst("div#Player iframe, iframe")?.attr("src")) ?: return false
                loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
            true
        } catch (e: Throwable) {
            Log.e("DZBX", "iframeDecode error: ${e.message}")
            false
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        return try {
            Log.d("DZBX", "data » $data")
            val document = app.get(
                data,
                headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
                cookies = mapOf(
                    "LockUser"      to "true",
                    "isTrustedUser" to "true",
                    "dbxu"          to "1743289650198"
                ),
                interceptor = interceptor
            ).document

            var linkFound = false
            val mainIframe = document.selectFirst("div#video-area iframe, iframe[src*='/player/']")?.attr("src")
            if (mainIframe != null) {
                Log.d("DZBX", "mainIframe » $mainIframe")
                if (iframeDecode(data, mainIframe, subtitleCallback, callback)) {
                    linkFound = true
                }
            }

            document.select("div.video-toolbar option[value], select.selectBox option[value]").forEach {
                val altLink = fixUrlNull(it.attr("value")) ?: return@forEach
                try {
                    val subDoc = app.get(
                        altLink,
                        headers = mapOf("User-Agent" to USER_AGENT, "Referer" to data),
                        cookies = mapOf(
                            "LockUser"      to "true",
                            "isTrustedUser" to "true",
                            "dbxu"          to "1743289650198"
                        ),
                        interceptor = interceptor
                    ).document
                    val iframe = subDoc.selectFirst("div#video-area iframe, iframe[src*='/player/']")?.attr("src") ?: return@forEach
                    Log.d("DZBX", "alt iframe » $iframe")

                    if (iframeDecode(data, iframe, subtitleCallback, callback)) {
                        linkFound = true
                    }
                } catch (e: Throwable) {
                    Log.e("DZBX", "Option error: ${e.message}")
                }
            }

            linkFound
        } catch (e: Throwable) {
            Log.e("DZBX", "loadLinks error: ${e.message}")
            false
        }
    }
}
