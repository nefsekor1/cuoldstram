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
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziBox : MainAPI() {
    override var mainUrl              = "https://www.dizibox.live"
    override var name                 = "DiziBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override var sequentialMainPage            = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    // ! CloudFlare bypass
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)

            if (response.code in listOf(403, 503)) {
                return cloudflareKiller.intercept(chain)
            }

            val body = response.peekBody(1024 * 1024).string()
            if (body.contains("Just a moment") || body.contains("Checking your browser") || body.contains("cf-browser-verification") || body.contains("Attention Required")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/ulke/turkiye"              to "Yerli",
        "${mainUrl}/dizi-arsivi/page/SAYFA/"   to "Dizi Arşivi",
        "${mainUrl}/tur/aile/page/SAYFA/"      to "Aile",
        "${mainUrl}/tur/aksiyon/page/SAYFA"    to "Aksiyon",
        "${mainUrl}/tur/animasyon/page/SAYFA"  to "Animasyon",
        "${mainUrl}/tur/belgesel/page/SAYFA"   to "Belgesel",
        "${mainUrl}/tur/bilimkurgu/page/SAYFA" to "Bilimkurgu",
        "${mainUrl}/tur/biyografi/page/SAYFA"  to "Biyografi",
        "${mainUrl}/tur/dram/page/SAYFA"       to "Dram",
        "${mainUrl}/tur/drama/page/SAYFA"      to "Drama",
        "${mainUrl}/tur/fantastik/page/SAYFA"  to "Fantastik",
        "${mainUrl}/tur/gerilim/page/SAYFA"    to "Gerilim",
        "${mainUrl}/tur/gizem/page/SAYFA"      to "Gizem",
        "${mainUrl}/tur/komedi/page/SAYFA"     to "Komedi",
        "${mainUrl}/tur/korku/page/SAYFA"      to "Korku",
        "${mainUrl}/tur/macera/page/SAYFA"     to "Macera",
        "${mainUrl}/tur/muzik/page/SAYFA"      to "Müzik",
        "${mainUrl}/tur/muzikal/page/SAYFA"    to "Müzikal",
        "${mainUrl}/tur/reality-tv/page/SAYFA" to "Reality TV",
        "${mainUrl}/tur/romantik/page/SAYFA"   to "Romantik",
        "${mainUrl}/tur/savas/page/SAYFA"      to "Savaş",
        "${mainUrl}/tur/spor/page/SAYFA"       to "Spor",
        "${mainUrl}/tur/suc/page/SAYFA"        to "Suç",
        "${mainUrl}/tur/tarih/page/SAYFA"      to "Tarih",
        "${mainUrl}/tur/western/page/SAYFA"    to "Western",
        "${mainUrl}/tur/yarisma/page/SAYFA"    to "Yarışma"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.replace("SAYFA", "$page")
        val document = app.get(
            url,
            headers = mapOf("User-Agent" to USER_AGENT, "Referer" to "$mainUrl/"),
            cookies = mapOf(
                "LockUser"      to "true",
                "isTrustedUser" to "true",
                "dbxu"          to "1743289650198"
            ),
            interceptor = interceptor,
            cacheTime   = 60
        ).document

        val home = if (request.name == "Dizi Arşivi") {
            document.select("article.detailed-article").mapNotNull { it.toMainPageResult() }
        } else {
            val list = document.select("article.article-series-poster").mapNotNull { it.toMainPageResult() }
            if (list.isEmpty()) {
                document.select("article.detailed-article, article.grid-box").mapNotNull { it.toMainPageResult() }
            } else {
                list
            }
        }
        return newHomePageResponse(request.name, home)
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

        return document.select("article.detailed-article, article.article-series-poster, div.film-list div.item-relative").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
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

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            addActors(actors)
            addTrailer(trailer)
        }
    }

    private suspend fun iframeDecode(data: String, iframe: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
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
                val decryptedData = try { CryptoJS.decrypt(cryptPass, cryptData) } catch (e: Exception) { "" }
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
                val decodedAtob = atobData.decodeUri()
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
                val decodedAtob = atobData.decodeUri()
                val strAtob     = String(Base64.decode(decodedAtob, Base64.DEFAULT), Charsets.UTF_8)
                subDoc          = Jsoup.parse(strAtob)
            }

            val subFrame = fixUrlNull(subDoc.selectFirst("div#Player iframe, iframe")?.attr("src")) ?: return false
            loadExtractor(subFrame, "${mainUrl}/", subtitleCallback, callback)

        } else {
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
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
            } catch (e: Exception) {
                Log.e("DZBX", "Option error: ${e.message}")
            }
        }

        return linkFound
    }
}
