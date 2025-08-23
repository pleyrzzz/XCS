package com.owencz1998

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.runAllAsync

class SxyPrn : MainAPI() {
    override var mainUrl = "https://sxyprn.com"
    override var name = "Sxyprn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/new.html?page=" to "New Videos",
        "$mainUrl/new.html?sm=trending&page=" to "Trending",
        "$mainUrl/new.html?sm=views&page=" to "Most Viewed",
        "$mainUrl/popular/top-viewed.html?p=day" to "Popular - Day",
        "$mainUrl/popular/top-viewed.html" to "Popular - Week",
        "$mainUrl/popular/top-viewed.html?p=month" to "Popular - Month",
        "$mainUrl/popular/top-viewed.html?p=all" to "Popular - All Time",
        "$mainUrl/trending/blowjob.html?sm=trending&trends=61" to "Blowjob",
        "$mainUrl/trending/teens.html?sm=trending&trends=21" to "Teens",
        "$mainUrl/trending/onlyfans.html?sm=trending&trends=7" to "Onlyfans",
        "$mainUrl/trending/bdsm.html?sm=trending&trends=184" to "Bdsm",
        "$mainUrl/trending/stepsis.html?sm=trending&trends=497" to "Stepsis",
        "$mainUrl/trending/petite.html?sm=trending&trends=501" to "Petite",
       "$mainUrl/searches/teacher.html?sm=trending&trends=1165" to "Teacher"  
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        var pageStr = ((page - 1) * 30).toString()

        val document = if ("page=" in request.data) {
            app.get(request.data + pageStr).document
        } else if ("/blog/" in request.data) {
            pageStr = ((page - 1) * 20).toString()
            app.get(request.data.replace(".html", "$pageStr.html")).document
        } else {
            app.get(request.data.replace(".html", ".html/$pageStr")).document
        }
        val home = document.select("div.main_content div.post_el_small").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(
            list = HomePageList(
                name = request.name, list = home, isHorizontalImages = true
            ), hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.post_text")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a.js-pop")!!.attr("href"))
        var posterUrl = fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("src"))
        if (posterUrl == "") {
            posterUrl =
                fixUrl(this.select("div.vid_container div.post_vid_thumb img").attr("data-src"))
        }
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 0 until 15) {
            val document = app.get(
                "$mainUrl/${query.replace(" ", "-")}.html?page=${i * 30}"
            ).document
            val results = document.select("div.main_content div.post_el_small").mapNotNull {
                    it.toSearchResult()
                }
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
        val title = document.selectFirst("div.post_text")?.text()?.trim().toString()
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")
                ?.attr("content")
        )

        val recommendations = document.select("div.main_content div div.post_el_small").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.recommendations = recommendations
        }
    }

    private fun updateUrl(arg: MutableList<String>): MutableList<String> {
        arg[5] =
            (Integer.parseInt(arg[5]) - (generateNumber(arg[6]) + generateNumber(arg[7]))).toString()
        return arg
    }

    private fun generateNumber(arg: String): Int {
        val str = arg.replace(Regex("\\D"), "")
        var sut = 0
        for (element in str) {
            sut += Integer.parseInt(element.toString(), 10)
        }
        return sut
    }

   override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        runAllAsync(
            {
                document.select("div.post_el_wrap a.extlink").amap {
                    loadExtractor(it.attr("href"), "", subtitleCallback, callback)
                }
            },
            {
                val torrentLink = document.select("a.mpc_btn").attr("href")
                val doc = app.get(torrentLink).document
                val magnetLink = doc.select("a.md_btn").attr("href")
                callback.invoke(
                    newExtractorLink(
                        "$name[Magnet]",
                        "$name[Magnet]",
                        magnetLink,
                        ExtractorLinkType.MAGNET
                    )
                )
            },
        )

        // val parsed = AppUtils.parseJson<Map<String, String>>(
        //     document.select("span.vidsnfo").attr("data-vnfo")
        // )
        // parsed[parsed.keys.toList()[0]]
        // var url = parsed[parsed.keys.toList()[0]].toString()

        // var tmp = url.split("/").toMutableList()
        // tmp[1] += "8"
        // tmp = updateUrl(tmp)

        // url = fixUrl(tmp.joinToString("/"))

        // callback.invoke(
        //     newExtractorLink(
        //         source = this.name,
        //         name = this.name,
        //         url = url
        //     ) {
        //         this.referer = ""
        //         this.quality = Qualities.Unknown.value
        //     }
        // )
        return true
    }
}

