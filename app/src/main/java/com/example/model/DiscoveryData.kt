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

    companion object {
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
