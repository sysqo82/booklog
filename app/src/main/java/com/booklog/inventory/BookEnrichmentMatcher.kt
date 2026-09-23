package com.booklog.inventory

import java.util.Locale

object BookEnrichmentMatcher {

    fun isTitleMatch(localTitle: String, apiTitle: String, exactOnly: Boolean = false): Boolean {
        fun clean(t: String, removeSub: Boolean) = t.lowercase(Locale.ROOT)
            .replace(Regex("^the "), "")
            .let { if (removeSub) it.replace(Regex("[:\\-].*$"), "") else it }
            .replace(Regex("[^a-z0-9]"), "")

        if (exactOnly) {
            return clean(localTitle, false) == clean(apiTitle, false)
        }

        val cLocal = clean(localTitle, true)
        val cApi = clean(apiTitle, true)
        return (cLocal.isNotEmpty()) && (cLocal == cApi || cLocal.contains(cApi) || cApi.contains(cLocal))
    }

    fun isAuthorMatch(localAuthor: String, apiAuthors: List<String>?): Boolean {
        if (apiAuthors.isNullOrEmpty()) return true

        val cleanLocal = localAuthor.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
        return apiAuthors.any { apiAuthor ->
            val cleanApi = apiAuthor.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
            cleanApi.contains(cleanLocal) || cleanLocal.contains(cleanApi)
        }
    }

    fun isThumbnailUpgrade(current: String?, new: String?): Boolean {
        if (new.isNullOrBlank()) return false
        if (current.isNullOrBlank()) return true
        return (current.contains("openlibrary.org")) && (new.contains("google.com"))
    }

    fun isDescriptionUpgrade(current: String?, new: String?): Boolean {
        if (new.isNullOrBlank()) return false
        if (current.isNullOrBlank()) return true

        // If current is very short, almost anything is an upgrade
        if ((current.length < 30) && (new.length > 50)) return true

        // If current is a list of keywords and new has sentence structure
        val currentIsList = (current.count { it == ',' } > 3) && !current.contains(".")
        val newIsSynopsis = new.contains(".") && (new.length > current.length)
        if (currentIsList && newIsSynopsis) return true

        // Significant length increase
        return (new.length > (current.length + 100)) && new.contains(" ")
    }
}
