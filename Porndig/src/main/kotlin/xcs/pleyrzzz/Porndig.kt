package xcs.pleyrzzz

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.FormBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element

class Porndig : MainAPI() {
    override var mainUrl = "https://www.porndig.com"
    override var name = "Porndig"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
            "posts?type=date" to "Latest Videos",
            "posts?type=views" to "Most Viewed Videos",
            "posts?type=rating" to "Most Rated Videos",
            "posts?type=date&quality=2160" to "4K Videos",
            "posts?type=date&category_id=34&category_name=young" to "Young",
        "posts?type=date&category_id=1043&category_name=threesome" to "Threesome",
        "posts?type=date&category_id=90&category_name=xxx-scenario" to "Scenario",
        "posts?type=date&category_id=42&category_name=orgy" to "Orgy",
        "posts?type=date&category_id=1233&category_name=cosplay" to "Cosplay",
        "posts?type=date&category_id=57&category_name=fetish" to "Fetish",
    )

    fun getQueryParams(query: String): Map<String, String> {
        return query
            .substringAfter("?", "")
            .split("&")
            .mapNotNull {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    private fun getRequestBody (
        request_type:String = "post",
        request_name:String = "multifilter_videos",
        filter_type: String="ctr",
                                filter_period : String="month",
                                category: Pair<String, String>? = null,
                                query: String?= null,
                                quality: String = "1080", page:Int=1) : FormBody
    {
        val postsOnPage = 50
        val offset: Int = (page - 1) * postsOnPage
        val build= FormBody.Builder()
            .addEncoded("main_category_id", "1")
            .addEncoded("type",request_type)
            .addEncoded("name",request_name)
            .addEncoded("filters[filter_type]",filter_type)
            .addEncoded("filters[filter_period]",filter_period)
            .addEncoded("filters[filter_quality][]",quality)
            .addEncoded("offset",offset.toString())
            .addEncoded("quantity",postsOnPage.toString())

            .addEncoded("filters[filter_duration][]", "45")
            .addEncoded("filters[filter_duration][]", "26")
            .addEncoded("filters[filter_duration][]", "15")
            .addEncoded("filters[filter_duration][]", "14")

        if(category!=null){
            build.addEncoded("multifilter[${category.first}]",category.second)
        }

        if(!query.isNullOrEmpty()){
            build.add("search",query)
        }
        return build.build()
    }


    override suspend fun getMainPage(
            page: Int,
            request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/posts/load_more_posts"

        val params = getQueryParams(request.data)

        var categoryFilter: Pair<String, String>? = null
        if(!params.get("category_id").isNullOrEmpty()){
            categoryFilter = Pair(params.get("category_id")?:"", params.get("category_name")?:"")
        }

        val requestBody = getRequestBody(filter_type = params.get("type")?:"ctr",
            category = categoryFilter,
            quality = params.get("quality")?:"1080",page=page)
        val json =app.post(url, referer = mainUrl, requestBody =  requestBody).toString()


        val jsonObject = JSONObject(json)
        val success = jsonObject.getBoolean("success")
        if(success) {
            val data = jsonObject.getJSONObject("data")
            val content = data.getString("content")
            val has_more = data.getBoolean("has_more")

            val document = Jsoup.parse(content)

            val home =
                document.select( ".video_item_wrapper, .video_block_wrapper")
                    .mapNotNull {
                        it.toSearchResult()
                    }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = has_more
            )
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = emptyList(),
                isHorizontalImages = true
            ),
            hasNext = false
        )

    }

    private fun fixPosterUrl(posterUrl: String?): String?{
        if(!posterUrl.isNullOrEmpty()){
            return posterUrl.replace("120x68","480x270")
                .replace("320x180","480x270")
                .replace("400x225","480x270").replace("https:","http:");
        }
        return posterUrl;
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".video_item_title h2,.video_item_title > .video_item_section_txt")?.text() ?: return null

        val href = fixUrl(this.selectFirst("a.video_item_thumbnail")!!.attr("href"))
        val posterUrl = fixPosterUrl(fixUrlNull(this.select(".video_item_thumbnail img").attr("data-src")))

        var quality:SearchQuality? = null

        val qIcon = this.selectFirst(".icon_19")

        if(qIcon!=null){
            if(qIcon.hasClass("icon-ic_19_qlt_4k")){
                quality= SearchQuality.FourK
            }
            if(qIcon.hasClass("icon-ic_19_qlt_full_hd")){
                quality= SearchQuality.HD
            }
            if(qIcon.hasClass("icon-ic_19_qlt_hd")){
                quality= SearchQuality.HQ
            }
            if(qIcon.hasClass("icon-ic_19_qlt_sd")){
                quality= SearchQuality.SD
            }
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
            this.posterHeaders = mapOf(Pair("referer", "${mainUrl}/"))

            if(quality !=null){
                this.quality = quality
            }
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..15) {
            val url: String ="$mainUrl/posts/load_more_search_posts/"

            val requestBody = getRequestBody(request_type = "search_post",
                filter_period = "",
                query = query,
                request_name = "search_posts")
           val json = app.post(url, referer = mainUrl, requestBody =  requestBody).toString()


            val jsonObject = JSONObject(json)
            val success = jsonObject.getBoolean("success")
            if(success) {
                val data = jsonObject.getJSONObject("data")
                val content = data.getString("content")
                val has_more = data.getBoolean("has_more")

                val document = Jsoup.parse(content)


                val results =
                    document.select(".video_item_wrapper, .video_block_wrapper")
                        .mapNotNull {
                            it.toSearchResult()
                        }
                searchResponse.addAll(results)
                if (results.isEmpty()) break
            }
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val jsonString = document.selectFirst("script[type=application/ld+json]")?.data().toString()
        val jsonObject = JSONObject(jsonString)

        val title = jsonObject.getString("name")
        val description = jsonObject.getString("description")
        val poster =
                fixUrlNull(jsonObject.getString("thumbnailUrl"))

        val tags = jsonObject.getString("keywords").split(",").map { it.replace("-", "") }.filter { it.isNotBlank() && !StringUtil.isNumeric(it) }

        val recommendations =
                document.select(".js_content_related_posts .video_item_wrapper").mapNotNull {
                    it.toSearchResult()
                }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.posterHeaders = mapOf(Pair("referer", "${mainUrl}/"))
            this.plot = description
            this.tags = tags
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

        document.select("a.post_download_link").map {
            val href= it.attr("href")

            val qText = it.select(".link_name").text().trim()

            callback.invoke(newExtractorLink(
                source = name,
                name = name,
                url = fixUrl(href)
            ) {
                this.referer = data
                if(qText.contains("4K")){
                    this.quality = 2160
                }
                else {
                    this.quality = Regex("(\\d+)p").find(qText)?.groupValues?.get(1)
                        .let { getQualityFromName(it) }
                }
            })
        }
        return true
    }
}