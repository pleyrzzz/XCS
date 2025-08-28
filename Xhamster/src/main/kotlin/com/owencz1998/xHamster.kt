// ! https://codeberg.org/coxju/cs-ext-coxju/src/branch/master/Xhamster/src/main/kotlin/com/coxju/Xhamster.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

@Suppress("ClassName")
class xHamster : MainAPI() {
    override var mainUrl              = "https://xhamster.com"
    override var name                 = "xHamster"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/hd/newest/"              to "Newest",
        "${mainUrl}/hd/most-viewed/weekly/"  to "Most viewed weekly",
        "${mainUrl}/hd/most-viewed/monthly/" to "Most viewed monthly",
        "${mainUrl}/hd/most-viewed"          to "Most viewed all time",
        "${mainUrl}/hd/most-viewed/weekly/"  to "Most viewed weekly",
        "${mainUrl}/4k/"  to "4k",
        "${mainUrl}/categories/teen/hd/"  to "Teen",
        "${mainUrl}/categories/compilation/hd/"  to "Compilation",
        "${mainUrl}/categories/group-sex/hd/"  to "Group Sex",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page + "?x_platform_switch=desktop").document
        val home     = document.select("div.thumb-list div.thumb-list__item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("a.video-thumb-info__name")?.text() ?: return null
        val href      = fixUrl(this.selectFirst("a.video-thumb-info__name")!!.attr("href"))
        val posterUrl = fixUrlNull(this.select("img.thumb-image-container__image").attr("src"))

        val is4K = !this.select(".thumb-image-container__on-video .beta-thumb-uhd").isEmpty()
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
            if(is4K){
                quality = SearchQuality.FourK
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 0 until 15) {
            val document = app.get("${mainUrl}/search/${query.replace(" ", "+")}/?page=$i&x_platform_switch=desktop").document

            val results = document.select("div.thumb-list div.thumb-list__item").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title           = document.selectFirst("div.with-player-container h1")?.text()?.trim().toString()
        val poster          = fixUrlNull(document.selectFirst("div.xp-preload-image")?.attr("style")?.substringAfter("https:")?.substringBefore("\');"))
        val tags            = document.select("nav#video-tags-list-container a.tag-96c3e .label-96c3e").map { it.text() }
        val recommendations = document.select("div.mixed-section.videos div.thumb-list--related div.thumb-list__item").mapNotNull { it.toSearchResult() }

        val desc = document.selectFirst(".controls-info .ab-info p")?.text()?.trim().toString()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.plot = desc
            this.posterUrl       = poster
            this.tags            = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val videoUrl = fixUrl(app.get(url = data).document.selectXpath("//link[contains(@href,'.m3u8')]")[0].attr("href"))

        var videoFound = false
        if(videoUrl.isEmpty()) return false
        try {
            M3u8Helper.generateM3u8( source = name, streamUrl = videoUrl, referer = data ).forEach { link -> callback(link); videoFound = true}
        } catch (e: Exception) {
            Log.e(name, "M3u8Helper failed: ${e.message}")
            callback( newExtractorLink( source = name, name = "$name HLS", url = videoUrl) { this.referer = data; this.quality = Qualities.Unknown.value; this.type = ExtractorLinkType.M3U8 } ); videoFound = true
        }

        if (!videoFound) Log.w(name, "No video links (M3U8) were found.")
        return videoFound
    }
}