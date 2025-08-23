package com.owencz1998

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONObject

class NoodleMagazineProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://tyler-brown.com"
    override var name = "Noodle Magazine"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.NSFW
    )

    override val mainPage = mainPageOf(
        "latest" to "Latest",
        "masterbation" to "Masterbation",
        "solo" to "Solo",
        "teen" to "Teen",
        "amateur" to "Amateur", 
        "lesbian" to "Lesbian",
        "gloryhole" to "Gloryhole",
        "onlyfans" to "Onlyfans",
        "latina" to "Latina",
        "blonde" to "Blonde",
        "milf" to "MILF",
        "ganbang" to "Gangbang",
        "public" to "Public", 
        "fingering" to "Fingering",
        "dildo" to "Dildo",
        "hardcore" to "Hardcore", 
        "sex%20machine" to "Sex Machine",
        "bella%20delphine" to "Bella Delphine",
        "jav" to "JAV",
        "hentai" to "Hentai"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val curpage = page - 1
        val link = "$mainUrl/video/${request.data}?p=$curpage"
        val document = app.get(link).document
        val home = document.select("div.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): MovieSearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val title = this.selectFirst("a div.i_info div.title")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a div.i_img img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<MovieSearchResponse> {
        val searchresult = mutableListOf<MovieSearchResponse>()

        (0..10).toList().apmap { page ->
            val doc = app.get("$mainUrl/video/$query?p=$page").document
            doc.select("div.item").apmap { res ->
                res.toSearchResult()?.let { searchresult.add(it) }
            }
        }

        return searchresult
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.l_info h1")?.text()?.trim() ?: "null"
        val poster = document.selectFirst("""meta[property="og:image"]""")?.attr("content") ?: "null"

        val recommendations = document.select("div.item").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val script = document.selectFirst("script:containsData(playlist)")

        if (script != null) {
            val jsonString = script.data()
                .substringAfter("window.playlist = ")
                .substringBefore(";")
            val jsonObject = JSONObject(jsonString)
            val sources = jsonObject.getJSONArray("sources")
            val extlinkList = mutableListOf<ExtractorLink>()
            val headers = mapOf(
                "Accept" to "*/*",
                "Sec-Fetch-Dest" to "video",
                "Sec-Fetch-Mode" to "no-cors",
                "Sec-Fetch-Site" to "cross-site",
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )

            for (i in 0 until sources.length()) {
                val source = sources.getJSONObject(i)
                extlinkList.add(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = source.getString("file"),
                        type = ExtractorLinkType.VIDEO,
                    ) {
                        this.referer = mainUrl
                        this.quality = getQualityFromName(source.getString("label"))
                        this.headers = headers
                    }
                )
            }
            extlinkList.forEach(callback)
        }
        return true
    }
}