package com.LongVideos

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

class Longvideos : MainAPI() {
    override var mainUrl              = "https://www.longvideos.xxx"
    override var name                 = "Longvideos"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "latest-updates" to "Latest",
        "most-popular" to "Most Viewed",
        "networks/brazzers-com/latest-updates" to "Brazzers",
        "networks/tushy-com/latest-updates" to "Tushy",
        "networks/adult-time/latest-updates" to "Adult Time",
        "sites/dorcel-club/latest-updates" to "Dorcel",
        "categories/hardcore/latest-updates" to "Hardcore",
        "categories/teen/latest-updates" to "Teen",
        "categories/group-sex/latest-updates" to "Group Sex",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page/").document
        val home     = document.select("div.list-videos div.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
                list    = HomePageList(
                name    = request.name,
                list    = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("a").attr("title")
        val href      = this.select("a").attr("href")
        var posterUrl = this.select("img").attr("src")

        if(posterUrl.contains("data:image")) {
            posterUrl = this.select("img").attr("data-src")
        }

        val searchResponse =  newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }

//        val is4K = this.select("div.wrap div.k4").isNotEmpty();
//        val preview = this.select("div.thumb__img").attr("data-preview");
//
//        if (is4K)
//            searchResponse.quality = SearchQuality.UHD

        return searchResponse
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..7) {
            val document = app.get("$mainUrl/search/$i/?q=$query").document
            val results  = document.select("div.list-videos div.item").mapNotNull { it.toSearchResult() }

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

        val title       = document.select("meta[property=og:title]").attr("content")
        val poster      = document.select("meta[property='og:image']").attr("content")
        val description = document.select("meta[property=og:description]").attr("content")

        val videoId = document.selectXpath("//script[contains(text(),'videoId')]").first()?.data()
            ?.substringAfter("videoId: '")?.substringBefore("'")

        val tags = document.select("div.hidden_tags div a.btn_tag").map { it.text() }
        val models = document.select("div.block-details div.item a.btn_model").map { it.text() }

        val recommendations =
            document.select("div.related-video div.list-videos div.item").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
            this.tags = tags
            this.recommendations = recommendations
            addActors(models)

            if(!videoId.isNullOrEmpty()){
                addTrailer("https://cast.longvideos.xxx/preview/$videoId.mp4")
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select("video.video-js > source").forEach {
            val url     = it.attr("src")
            val quality = it.attr("label").replace("p", "").toIntOrNull() ?: Qualities.Unknown.value
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url,
                    type = ExtractorLinkType.VIDEO,
                ) {
                    this.quality = quality
                    this.referer = data
                }
            )
        }
        return true
    }
}
