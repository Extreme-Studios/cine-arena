package com.extremestudios.cinearena.data.model

data class PortalPost(
    val id: String,
    val title: String,
    val submitterName: String,
    val category: String,
    val posterUrl: String,
    val targetUrl: String,
    val shortCaption: String,
    val eventDate: String,
    val status: String,
    val createdAt: String
)
