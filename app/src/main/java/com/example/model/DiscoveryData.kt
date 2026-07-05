package com.example.model

data class Show(
    val title: String,
    val description: String,
    val audioUrl: String = "",
    val link: String = "",
    val duration: Int = 0,
    val imageUrl: String = ""
)

data class Category(
    val name: String,
    val shows: List<Show>,
    val imageUrl: String = ""
)