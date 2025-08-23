package com.Porn4fans

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Porn4fans : MainAPI() {
    override var mainUrl              = "https://www.porn4fans.com"
    override var name                 = "Porn4fans"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

        override val mainPage = mainPageOf(
        "$mainUrl/onlyfans-videos/%d/" to "Latest",
        "$mainUrl/onlyfans-videos/%d/?p=video_viewed&post_date_from=7" to "Most Viewed(This Week)",
        "$mainUrl/categories/roleplay-fantasy/%d/" to "Roleplay",
        "$mainUrl/categories/pornstar/%d/" to "Pornstars",
        "$mainUrl/onlyfans-videos/%d/?p=video_viewed&post_date_from=30" to "Most Viewed(Month)",
        "$mainUrl/onlyfans-videos/%d/?p=video_viewed" to "Most Viewed(All time)",
        "$mainUrl/categories/petite/%d/" to "Petite",
        "$mainUrl/categories/Eighteen/%d/" to "18+",
        "$mainUrl/categories/masturbation/%d/" to "Masturbation",
        "$mainUrl/categories/miniskirt/%d/" to "Miniskirt",
        "$mainUrl/categories/blowjob/%d/" to "Blowjob",
        "$mainUrl/categories/lesbian/%d/" to "Lesbian",
        "$mainUrl/categories/titfuck/%d/" to "Titty Fuck",
        "$mainUrl/categories/anal/%d/" to "Anal",
        "$mainUrl/categories/gothic/%d/" to "Gothic",
        "$mainUrl/categories/big-dick/%d/" to "Big Dick",
        "$mainUrl/categories/spooning/%d/" to "Spoon Fucking",
        "$mainUrl/categories/pale/%d/" to "Pale",
        "$mainUrl/categories/nympho/%d/" to "Nympho",
        "$mainUrl/categories/big-natural-tits/%d/" to "Big Natural Tits",
        "$mainUrl/categories/small-tits/%d/" to "Small Tits",
        "$mainUrl/categories/milf/%d/" to "Milf",
        "$mainUrl/categories/threesome/%d/" to "Threesome",
        "$mainUrl/categories/perfect-porn/%d/" to "Perfect Porn",
        "$mainUrl/categories/redhead/%d/" to "Redhead",
        "$mainUrl/categories/glasses/%d/" to "Glasses",
        "$mainUrl/categories/stockings/%d/" to "Stockings",
        "$mainUrl/categories/black-hair-caucasian/%d/" to "Black Hair Caucasian",
        "$mainUrl/categories/blonde/%d/" to "Blonde",
        "$mainUrl/categories/ponytail/%d/" to "Ponytail",
        "$mainUrl/categories/pigtail/%d/" to "Pigtail",
        "$mainUrl/categories/hardcore/%d/" to "Hardcore",
        "$mainUrl/categories/short-hair/%d/" to "Short Hair",
        "$mainUrl/categories/deepthroat/%d/" to "Deepthroat",
        "$mainUrl/categories/lingerie/%d/" to "Lingerie",
        "$mainUrl/categories/british/%d/" to "British",
        "$mainUrl/categories/bubble-butt/%d/" to "Roleplay",
        "$mainUrl/categories/freckles/%d/" to "Freckles",
        "$mainUrl/categories/blue-eyes/%d/" to "Blue Eyes",
        "$mainUrl/categories/brunette/%d/" to "Brunette",
        "$mainUrl/categories/interracial/%d/" to "Interracial",      
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data.format(page)).document
        val home     = document.select("div.item").mapNotNull { it.toSearchResult() }

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
        val posterUrl = this.select("img").attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..7) {
            val document = app.get("$mainUrl/search/$query/?mode=async&function=get_block&block_id=custom_list_videos_videos_list_search_result&q=$query&category_ids&sort_by&from_videos=$i&from_albums=$i").document
            val results = document.select("div.item").mapNotNull { it.toSearchResult() }

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
        val jsonString = document.selectFirst("script[type=application/ld+json]")?.data().toString()
        val jsonObject = parseJson<Response>(jsonString)

        return newMovieLoadResponse(jsonObject.name, url, TvType.NSFW, jsonObject.contentUrl) {
            this.posterUrl = jsonObject.thumbnailUrl
            this.plot      = jsonObject.description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        callback.invoke(
            newExtractorLink(
                source = "Porn4fans",
                name = "Porn4fans",
                url = data
            ) {
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    data class Response(
        val name: String,
        val thumbnailUrl: String,
        val description: String,
        val contentUrl: String
    )
}