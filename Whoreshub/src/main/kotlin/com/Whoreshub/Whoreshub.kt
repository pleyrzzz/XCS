package com.Whoreshub

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Whoreshub : MainAPI() {
    override var mainUrl              = "https://www.whoreshub.com"
    override var name                 = "Whoreshub"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/latest-updates" to "Latest",
        "${mainUrl}/most-popular" to "Most Popular",
        "${mainUrl}/top-rated" to "Top Rated",
        "${mainUrl}/categories/solo" to "Solo",
        "${mainUrl}/categories/masturbation" to "Masturbation",
        "${mainUrl}/categories/public" to "Public",
        "${mainUrl}/categories/toys" to "Toys",
        "${mainUrl}/categories/cumshot" to "Cumshot",
        "${mainUrl}/categories/petite" to "Petite",
        "${mainUrl}/categories/amateur" to "Amateur",
        "${mainUrl}/categories/teen" to "Teen",
        "${mainUrl}/categories/homemade" to "Homemade",
        "${mainUrl}/categories/ass-to-mouth" to "Ass to mouth",
        "${mainUrl}/categories/anal" to "Anal",
        "${mainUrl}/categories/small-tits" to "Small Tits",
        "${mainUrl}/categories/big-tits" to "Big Tits",
       "${mainUrl}/categories/cosplay" to "Cosplay",
       "${mainUrl}/categories/handjob" to "Handjob",
       "${mainUrl}/categories/babysitter" to "Babysitter",
       "${mainUrl}/categories/milf" to "Milf",
       "${mainUrl}/categories/threesome" to "Threesome",
       "${mainUrl}/categories/deepthroat" to "Deepthroat",
       "${mainUrl}/categories/fisting" to "Fisting",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/${page}/").document
        val home = document.select("div.block-thumbs a.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.attr("title")
        val href = this.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        val document = app.get("${mainUrl}/search/?q=${query}").document

        val results = document.select("div.block-thumbs a.item").mapNotNull { it.toSearchResult() }

        searchResponse.addAll(results)

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content") ?:""
        val posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
        ): Boolean {

        val document = app.get(data).document
        val docText = document.toString()
        val regex = Regex("""video_(?:url|alt_url(?:2|3)?): '(https://[^']+)'""")
        val links = regex.findAll(docText).map { it.groupValues[1] }.toList()
        for(link in links) {
            if(link.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = link
                    ) {
                        this.referer = ""
                        this.quality = Regex("""_(1080|720|480|360)p\.mp4""")
                            .find(link)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Qualities.Unknown.value
                    }
                )
            }
        }

        return true
    }
}