package com.extremestudios.cinearena.data.model

data class PortalUserSession(
    val userId: String,
    val bandName: String,
    val genre: String,
    val city: String,
    val whatsappNormalized: String,
    val createdAt: String,
    val status: String
)
