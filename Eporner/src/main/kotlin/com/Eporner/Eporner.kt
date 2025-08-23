package com.Eporner

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.math.BigInteger

class Eporner : MainAPI() {
    override var mainUrl              = "https://www.eporner.com"
    override var name                 = "Eporner"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
            "" to "Recent Videos",
            "best-videos" to "Best Videos",
            "top-rated" to "Top Rated",
            "most-viewed" to "Most Viewed",
            "cat/teens" to "Teen",
            "cat/hardcore" to "Hardcore",
            "cat/threesome" to "Threesome",
            "cat/group-sex" to "Group Sex",
            "cat/hd-1080p" to "1080 Porn",
            "cat/4k-porn" to "4K Porn",
            "recommendations" to "Recommendation Videos",
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val reqUrl = if(page > 1) "$mainUrl/${request.data}/$page/" else "$mainUrl/${request.data}/"

        val document = app.get(reqUrl, referer = "$mainUrl/").document
        val home = document.select("#div-search-results div.mb").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list    = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = fixTitle(this.select("div.mbunder p.mbtit a").text() ?: "No Title").trim()
        val href = fixUrl(this.select("div.mbcontent a").attr("href"))
        var posterUrl = this.selectFirst("img")?.attr("data-src")
        if (posterUrl.isNullOrBlank())
        {
            posterUrl=this.selectFirst("img")?.attr("src")
        }

        val qualityStr = this.selectFirst(".mvhdico span")?.text()
        var quality:SearchQuality? = null
        if(!qualityStr.isNullOrEmpty()) {
            when {
                qualityStr.contains("4k", true) -> SearchQuality.FourK
                qualityStr.contains("2k", true) -> SearchQuality.UHD
                qualityStr.contains("1080", true) -> SearchQuality.HD
                qualityStr.contains("720", true) -> SearchQuality.HQ
                qualityStr.contains("480", true) -> SearchQuality.SD
                else -> SearchQuality.WebRip
            }.also { quality = it }
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl

            if(quality !=null){
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val subquery=query.replace(" ","-")
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..10) {
            val document = app.get("${mainUrl}/search/$subquery/$i").document
            //val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.mb").mapNotNull {
                it.toSearchResult() }

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

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        val tags = document.select("#video-info-tags li.vit-category a").map { it.text() }

        val recommendations =
            document.select("#relateddiv div.mb").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
            this.tags = tags
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc= app.get(data).toString()
        val vid=Regex("EP.video.player.vid = '([^']+)'").find(doc)?.groupValues?.get(1).toString()
        val hash=Regex("EP.video.player.hash = '([^']+)'").find(doc)?.groupValues?.get(1).toString()
        val url="https://www.eporner.com/xhr/video/$vid?hash=${base36(hash)}"
        //Log.d("Phisher",url)
        val json= app.get(url).toString()
        val jsonObject = JSONObject(json)
        val sources = jsonObject.getJSONObject("sources")
        val mp4Sources = sources.getJSONObject("mp4")
        val qualities = mp4Sources.keys()
        while (qualities.hasNext()) {
            val quality = qualities.next() as String
            val sourceObject = mp4Sources.getJSONObject(quality)
            val src = sourceObject.getString("src")
            val labelShort = sourceObject.getString("labelShort") ?: ""
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = src,
                    type = INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(labelShort)
                }
            )
        }
        return true
    }
// Thanks to https://github.com/alfa-addon/addon/blob/2a3c9d5e4d35f8420e680d2ee8dd31291bbc727e/plugin.video.alfa/servers/eporner.py#L26 for Code
    fun base36(hash: String): String {
        return if (hash.length >= 32) {
            // Split the hash into 4 parts, convert each part to base36, and concatenate the results
            val part1 = BigInteger(hash.substring(0, 8), 16).toString(36)
            val part2 = BigInteger(hash.substring(8, 16), 16).toString(36)
            val part3 = BigInteger(hash.substring(16, 24), 16).toString(36)
            val part4 = BigInteger(hash.substring(24, 32), 16).toString(36)

            part1 + part2 + part3 + part4
        } else {
            throw IllegalArgumentException("Hash length is invalid")
        }
    }
    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "") ?. groupValues ?. getOrNull(1) ?. toIntOrNull()
            ?: Qualities.Unknown.value
    }
}