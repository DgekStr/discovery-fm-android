package com.example.model

import android.os.Parcel
import android.os.Parcelable

data class Show(
    val title: String,
    val description: String,
    val audioUrl: String = "",
    val link: String = "",
    val duration: Int = 0,
    val imageUrl: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(description)
        parcel.writeString(audioUrl)
        parcel.writeString(link)
        parcel.writeInt(duration)
        parcel.writeString(imageUrl)
    }

    override fun describeContents(): Int = 0

    /**
     * Возвращает название без нежелательных символов-кавычек (" и «»).
     */
    val cleanTitle: String
        get() = cleanDisplayTitle(title)

    companion object {
        /**
         * Очищает название от лишних кавычек.
         * Убирает прямые кавычки (?), ёлочки (?) и их HTML-сущности
         * (&quot;, &#34;, &apos;, &#39;), а также схлопывает лишние пробелы.
         */
        fun cleanDisplayTitle(raw: String): String {
            return raw
                .replace("&amp;quot;", "")
                .replace("&amp;#34;", "")
                .replace("&amp;apos;", "")
                .replace("&amp;#39;", "")
                .replace("&quot;", "")
                .replace("&#34;", "")
                .replace("&apos;", "")
                .replace("&#39;", "")
                .replace("\"", "")
                .replace("\u00AB", "")
                .replace("\u00BB", "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }

        @JvmField
        val CREATOR = object : Parcelable.Creator<Show> {
            override fun createFromParcel(parcel: Parcel): Show = Show(parcel)
            override fun newArray(size: Int): Array<Show?> = arrayOfNulls(size)
        }
    }
}

data class Category(
    val name: String,
    val shows: List<Show>,
    val imageUrl: String = ""
)
