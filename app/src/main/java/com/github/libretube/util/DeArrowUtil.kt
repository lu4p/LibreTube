package com.github.libretube.util

import android.util.Log
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.RetrofitInstance
import com.github.libretube.api.obj.ContentItem
import com.github.libretube.api.obj.DeArrowContent
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.PreferenceHelper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.TreeSet

object DeArrowUtil {
    private fun extractTitleAndThumbnail(data: JsonElement): Pair<String?, String?> {
        val content = try {
            JsonHelper.json.decodeFromJsonElement<DeArrowContent>(data)
        } catch (e: Exception) {
            return null to null
        }
        val newTitle = content.titles.firstOrNull { it.votes >= 0 || it.locked }?.title
        val newThumbnail = content.thumbnails.firstOrNull {
            it.thumbnail != null && !it.original && (it.votes >= 0 || it.locked)
        }?.thumbnail
        return newTitle to newThumbnail
    }

    /**
     * Apply the new titles and thumbnails generated by DeArrow to the stream items
     */
    suspend fun deArrowStreamItems(streamItems: List<StreamItem>): List<StreamItem> {
        if (!PreferenceHelper.getBoolean(PreferenceKeys.DEARROW, false)) return streamItems

        val videoIds = streamItems.mapNotNullTo(TreeSet()) { it.url?.toID() }
            .joinToString(",")

        val response = try {
            RetrofitInstance.api.getDeArrowContent(videoIds)
        } catch (e: Exception) {
            Log.e(this::class.java.name, e.toString())
            return streamItems
        }
        for ((videoId, data) in response.entries) {
            val (newTitle, newThumbnail) = extractTitleAndThumbnail(data)
            val streamItem = streamItems.firstOrNull { it.url?.toID() == videoId }
            newTitle?.let { streamItem?.title = newTitle }
            newThumbnail?.let { streamItem?.thumbnail = newThumbnail }
        }
        return streamItems
    }

    /**
     * Apply the new titles and thumbnails generated by DeArrow to the stream items
     */
    suspend fun deArrowContentItems(contentItems: List<ContentItem>): List<ContentItem> {
        if (!PreferenceHelper.getBoolean(PreferenceKeys.DEARROW, false)) return contentItems

        val videoIds = contentItems.filter { it.type == "stream" }
            .mapTo(TreeSet()) { it.url.toID() }
            .joinToString(",")

        if (videoIds.isEmpty()) return contentItems

        val response = try {
            RetrofitInstance.api.getDeArrowContent(videoIds)
        } catch (e: Exception) {
            Log.e(this::class.java.name, e.toString())
            return contentItems
        }
        for ((videoId, data) in response.entries) {
            val (newTitle, newThumbnail) = extractTitleAndThumbnail(data)
            val contentItem = contentItems.firstOrNull { it.url.toID() == videoId }
            newTitle?.let { contentItem?.title = newTitle }
            newThumbnail?.let { contentItem?.thumbnail = newThumbnail }
        }
        return contentItems
    }
}

/**
 * If enabled in the preferences, this overrides the video's thumbnail and title with the one
 * provided by the DeArrow project
 */
@JvmName("deArrowStreamItems")
suspend fun List<StreamItem>.deArrow() = DeArrowUtil.deArrowStreamItems(this)

/**
 * If enabled in the preferences, this overrides the video's thumbnail and title with the one
 * provided by the DeArrow project
 */
@JvmName("deArrowContentItems")
suspend fun List<ContentItem>.deArrow() = DeArrowUtil.deArrowContentItems(this)
