package com.extremestudios.cinearena.data.model

data class SongRegistrationPayload(
    val title: String,
    val release: String,
    val category: String,
    val duration: String,
    val director: String,
    val castName: String,
    val synopsis: String,
    val copyrightAgreement: Boolean,
    val trailerUrl: String? = null,
    val movieUrl: String? = null,
    val trailerFileBase64: String? = null,
    val trailerFileName: String? = null,
    val trailerFileMimeType: String? = null,
    val coverImageBase64: String? = null,
    val coverImageFileName: String? = null,
    val coverImageMimeType: String? = null
)
