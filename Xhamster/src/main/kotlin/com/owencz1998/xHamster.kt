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

    // Add SearchResult structure
    data class SearchResult(
        @JsonProperty("videoThumbProps") val videoThumbProps: List<VideoThumbProps>? = null
        // Add other search result properties if needed
    )

    // Modify InitialsJson to include searchResult
    data class InitialsJson(
        // For Video Page (load/loadLinks)
        val xplayerSettings: XPlayerSettings? = null,
        val videoEntity: VideoEntity? = null,
        val videoTagsComponent: VideoTagsComponent? = null,
        val relatedVideos: RelatedVideos? = null,
        // For Main Page (getMainPage)
        val layoutPage: LayoutPage? = null,
        // For Search Page (search)
        val searchResult: SearchResult? = null // Add searchResult key
    )

    data class XPlayerSettings(
        val sources: VideoSources? = null,
        val poster: Poster? = null,
        val subtitles: Subtitles? = null
    )

    data class VideoSources(
        val hls: HlsSources? = null,
        val standard: StandardSources? = null
    )

    data class HlsSources(
        val h264: HlsSource? = null
    )

    data class StandardSources(
        val h264: List<StandardSourceQuality>? = null
    )

    data class HlsSource(
        val url: String? = null
    )

    data class StandardSourceQuality(
        val quality: String? = null,
        val url: String? = null
    )

    data class Poster(
        val url: String? = null
    )

    data class Subtitles(
        val tracks: List<SubtitleTrack>? = null
    )

    data class SubtitleTrack(
        val label: String? = null,
        val lang: String? = null,
        val urls: SubtitleUrls? = null
    )

    data class SubtitleUrls(
        val vtt: String? = null
    )

    data class VideoEntity(
        val title: String? = null,
        val description: String? = null,
        val thumbBig: String? = null
    )

    data class VideoTagsComponent(
        val tags: List<Tag>? = null
    )

    data class Tag(
        val name: String? = null,
        val url: String? = null
    )

    data class RelatedVideos(
        val videoTabInitialData: VideoTabInitialData? = null
    )

    data class VideoTabInitialData(
        val videoListProps: VideoListProps? = null
    )

    data class LayoutPage( // For Main Page JSON structure
        @JsonProperty("videoListProps") val videoListProps: VideoListProps? = null
    )

    data class VideoListProps(
        val videoThumbProps: List<VideoThumbProps>? = null
    )

    data class VideoThumbProps(
        val title: String?,
        val pageURL: String?,
        @JsonProperty("thumbURL") val thumbUrl: String?
    )

    private fun getInitialsJson(html: String): InitialsJson? {
        return try {
            val script = Jsoup.parse(html).selectFirst("script#initials-script")?.html() ?: return null
            val jsonString = script.removePrefix("window.initials=").removeSuffix(";")
            AppUtils.parseJson<InitialsJson>(jsonString) // Use AppUtils.parseJson
        } catch (e: Exception) {
            Log.e(name, "getInitialsJson failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // ... (Code giữ nguyên) ...
        Log.d(name, "LoadLinks started for: $data")
        val document = try { app.get(data).document } catch (e: Exception) { Log.e(name, "Failed to get document for loadLinks: ${e.message}"); return false }
        val initialData = getInitialsJson(document.html()) ?: run { Log.e(name, "Failed to parse JSON for loadLinks."); return false }

        var foundLinks = false
        val sources = initialData.xplayerSettings?.sources
        val sourceName = this.name

        sources?.hls?.h264?.url?.let { m3u8Url ->
            val fixedM3u8Url = fixUrl(m3u8Url)
            Log.d(name, "Found HLS url: $fixedM3u8Url")
            try {
                M3u8Helper.generateM3u8( source = sourceName, streamUrl = fixedM3u8Url, referer = data ).forEach { link -> callback(link); foundLinks = true }
            } catch (e: Exception) {
                Log.e(name, "M3u8Helper failed: ${e.message}")
                callback( newExtractorLink( source = sourceName, name = "$sourceName HLS", url = fixedM3u8Url) { this.referer = data; this.quality = Qualities.Unknown.value; this.type = ExtractorLinkType.M3U8 } ); foundLinks = true
            }
        } ?: Log.w(name, "No HLS source found in JSON.")

        sources?.standard?.h264?.forEach { qualitySource ->
            val qualityLabel = qualitySource.quality
            val videoUrl = qualitySource.url
            if (qualityLabel != null && videoUrl != null) {
                val fixedVideoUrl = fixUrl(videoUrl)
                val quality = qualityLabel.replace("p", "").toIntOrNull() ?: Qualities.Unknown.value
                Log.d(name, "Found MP4 link: $qualityLabel - $fixedVideoUrl")
                callback( newExtractorLink( source = sourceName, name = "$sourceName MP4 $qualityLabel", url = fixedVideoUrl) { this.referer = data; this.quality = quality; this.type = ExtractorLinkType.VIDEO }); foundLinks = true
            } else { Log.w(name, "MP4 source item missing quality or url: $qualitySource") }
        } ?: Log.w(name, "No Standard H264 sources found in JSON.")

        initialData.xplayerSettings?.subtitles?.tracks?.forEach { track ->
            val subUrl = track.urls?.vtt
            val lang = track.lang ?: track.label ?: "Unknown"
            if (subUrl != null) {
                val fixedSubUrl = fixUrl(subUrl)
                Log.d(name, "Found subtitle: Lang=$lang, URL=$fixedSubUrl")
                try { subtitleCallback( SubtitleFile( lang = lang, url = fixedSubUrl )) } catch (e: Exception) { e.printStackTrace() }
            } else { Log.w(name, "Subtitle track missing VTT url: $track") }
        } ?: Log.w(name, "No subtitle tracks found in JSON.")

        if (!foundLinks) Log.w(name, "No video links (M3U8 or MP4) were found.")
        return foundLinks
    }

//    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
//        val videoUrl = fixUrl(app.get(url = data).document.selectXpath("//link[contains(@href,'.m3u8')]")[0].attr("href"))
//
//        val extlinkList = mutableListOf<ExtractorLink>()
//        M3u8Helper().m3u8Generation(
//                M3u8Helper.M3u8Stream(
//                    videoUrl
//                ), true
//            ).amap { stream ->
//            extlinkList.add(
//                newExtractorLink(
//                    source = name,
//                    name = name,
//                    url = stream.streamUrl,
//                    type = ExtractorLinkType.M3U8,
//                ) {
//                    quality = Regex("(\\d+)p").find(stream.streamUrl)?.groupValues?.last()
//                        .let { getQualityFromName(it) }
//                    referer = data
//                }
//            )
//        }
//
//        extlinkList.forEach(callback)
//        return true
//    }
}