package com.owencz1998

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject

class PornHubProvider : MainAPI() {
    private val globalTvType = TvType.NSFW
    override var mainUrl              = "https://www.pornhub.com"
    override var name                 = "PornHub"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/video?o=mr&hd=1&page="           to "Recently Featured",
        "${mainUrl}/video?o=tr&t=w&hd=1&page="       to "Top Rated",
        "${mainUrl}/video?o=mv&t=w&hd=1&page="       to "Most Viewed",
        "${mainUrl}/video?o=ht&t=w&hd=1&page="       to "Hottest",
        "${mainUrl}/video?p=professional&hd=1&page=" to "Professional",
        "${mainUrl}/video?o=cm&t=w&hd=1&page="       to "Newest",
        "${mainUrl}/video?c=35&hd=1&page="                to       "Anal",
        "${mainUrl}/video?c=27&hd=1&page="                to "Lesbian",
        "${mainUrl}/video?c=21&hd=1&page="                to "Hardcore",
        "${mainUrl}/video?c=57&hd=1&page="                 to "Compilation",
        "${mainUrl}/video?c=65&hd=1&page="                 to "Threesome",
        "${mainUrl}/video?c=2&hd=1&page="               to "Orgy",
        "${mainUrl}/video?c=8&hd=1&page="                 to "Big Tits",
        "${mainUrl}/video?c=13&hd=1&page="                to "Blowjob",
                       
    )
    private val cookies = mapOf(Pair("hasVisited", "1"), Pair("accessAgeDisclaimerPH", "1"))

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val categoryData = request.data
            val categoryName = request.name
            val pagedLink = if (page > 0) categoryData + page else categoryData
            val soup = app.get(pagedLink, cookies = cookies).document
            val home = soup.select("div.sectionWrapper div.wrap").mapNotNull {
                if (it == null) {
                    return@mapNotNull null
                }
                val title = it.selectFirst("span.title a")?.text() ?: ""
                val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val img = fetchImgUrl(it.selectFirst("img"))

                newMovieSearchResponse(
                    name = title,
                    url = link,
                    type = globalTvType,

                ){
                    posterUrl = img
                }
            }
            if (home.isNotEmpty()) {
                return newHomePageResponse(
                    list = HomePageList(
                        name = categoryName, list = home, isHorizontalImages = true
                    ), hasNext = true
                )
            } else {
                throw ErrorLoadingException("No homepage data found!")
            }
        } catch (e: Exception) {
            logError(e)
        }
        throw ErrorLoadingException()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/video/search?search=${query}"
        val document = app.get(url, cookies = cookies).document
        return document.select("div.sectionWrapper div.wrap").mapNotNull {
            if (it == null) {
                return@mapNotNull null
            }
            val title = it.selectFirst("span.title a")?.text() ?: return@mapNotNull null
            val link = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val image = fetchImgUrl(it.selectFirst("img"))
            newMovieSearchResponse(
                name = title,
                url = link,
                type = globalTvType
            ){posterUrl = image}
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val soup = app.get(url, cookies = cookies).document
        val title = soup.selectFirst(".title span")?.text() ?: ""
        val poster: String? = soup.selectFirst("div.video-wrapper .mainPlayerDiv img")?.attr("src")
            ?: soup.selectFirst("head meta[property=og:image]")?.attr("content")
        val tags = soup.select("div.categoriesWrapper a")
            .map { it?.text()?.trim().toString().replace(", ", "") }

        val recommendations = soup.select("ul#recommendedVideosListing li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(name = rTitle, url = rUrl, type = globalTvType){
                 posterUrl = rPoster
            }

        }

        val actors =
            soup.select("div.video-info-row div.pornstarsWrapper a.pstar-list-btn")
                .map { it.text() }

        val relatedVideo = soup.select("ul#relatedVideosListing li.pcVideoListItem").map {
            val rTitle = it.selectFirst("div.phimage a")?.attr("title") ?: ""
            val rUrl = fixUrl(it.selectFirst("div.phimage a")?.attr("href").toString())
            val rPoster = fixUrl(
                it.selectFirst("div.phimage img.js-videoThumb")?.attr("src").toString()
            )
            newMovieSearchResponse(name = rTitle, url = rUrl, type = globalTvType){
                this.posterUrl = rPoster
            }
        }

        return newMovieLoadResponse(title, url, globalTvType, url) {
            this.posterUrl = poster
            this.plot = title
            this.tags = tags
            addActors(actors)
            this.recommendations = recommendations + relatedVideo
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val request = app.get(
            url = data, cookies = cookies
        )
        val document = request.document
        val mediaDefinitions = JSONObject(
            document.selectXpath("//script[contains(text(),'flashvars')]").first()?.data()
                ?.substringAfter("=")?.substringBefore(";")
        ).getJSONArray("mediaDefinitions")

        for (i in 0 until mediaDefinitions.length()) {
            if (mediaDefinitions.getJSONObject(i).optString("quality") != null) {
                val quality = mediaDefinitions.getJSONObject(i).getString("quality")
                val videoUrl = mediaDefinitions.getJSONObject(i).getString("videoUrl")
                val extlinkList = mutableListOf<ExtractorLink>()
                M3u8Helper().m3u8Generation(
                    M3u8Helper.M3u8Stream(
                        videoUrl
                    ), true
                ).apmap { stream ->
                    extlinkList.add(
                        newExtractorLink(
                            source = name,
                            name = "${this.name}",
                            url = stream.streamUrl,
                            type = ExtractorLinkType.M3U8,
                        ) {
                            this.quality = Regex("(\\d+)").find(quality ?: "")?.groupValues?.get(1)
                            .let { getQualityFromName(it) }
                            this.referer = mainUrl
                        }
                    )
                }
                extlinkList.forEach(callback)
            }
        }

        return true
    }

    private fun fetchImgUrl(imgsrc: Element?): String? {
        return try {
            imgsrc?.attr("src") ?: imgsrc?.attr("data-src") ?: imgsrc?.attr("data-mediabook")
            ?: imgsrc?.attr("alt") ?: imgsrc?.attr("data-mediumthumb")
            ?: imgsrc?.attr("data-thumb_url")
        } catch (e: Exception) {
            null
        }
    }
}