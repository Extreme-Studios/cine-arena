const SPREADSHEET_ID = "1UNK85-L1roy2ynm9Locj2lyGda_QuASAtMAauJg5DDM";

const SHEET_MEMBERS = "members";
const SHEET_FILMS = "films";
const SHEET_FILM_LIKES = "film_likes";
const SHEET_PORTAL = "poster_posts";
const SHEET_PORTAL_REPORTS = "portal_reports";
const SHEET_VIDEO_UPLOADS = "video_uploads";
const PORTAL_POST_EXPIRE_HOURS = 3;
const PORTAL_POST_SUBMIT_COOLDOWN_MINUTES = 5;

const FILM_COVER_FOLDER_ID = "13UJYlo5D_oERwhPhvIDIhTbeLpufIai2";
const FILM_TRAILER_FOLDER_ID = "1zanbbmcrfMhr7nHPC-phi4CU4z3lNE6f";
const PORTAL_POSTER_FOLDER_ID = "1NDyHLVbEq93ojO6-Rm9A2A5PeHDWMEYq";

const MEMBER_HEADERS = [
  "id",
  "nama_kreator",
  "asal_sekolah",
  "whatsapp",
  "status",
  "createdAt",
  "role"
];

const FILM_HEADERS = [
  "id",
  "judul_film",
  "release",
  "category",
  "duration",
  "director",
  "nama_pemain",
  "synopsis",
  "cover_url",
  "trailer_url",
  "movie_url",
  "status",
  "views",
  "likes"
];

const FILM_LIKE_HEADERS = [
  "video_id",
  "user_id",
  "timestamp"
];

const PORTAL_HEADERS = [
  "id",
  "user_id",
  "creator_name",
  "whatsapp",
  "category",
  "poster_url",
  "posting_at",
  "status"
];

const PORTAL_REPORT_HEADERS = [
  "id",
  "post_id",
  "post_title",
  "category",
  "report_reason",
  "reported_link",
  "poster_url",
  "status"
];

const VIDEO_UPLOAD_HEADERS = [
  "id",
  "judul_video",
  "genre",
  "asal_sekolah",
  "whatsapp",
  "video_url",
  "status",
  "createdAt"
];

function setupCineArenaSheets() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const membersSheet = ensureSheetWithHeaders_(ss, SHEET_MEMBERS, MEMBER_HEADERS);
  removeLegacyMemberAdminNoteColumn_(membersSheet);
  const filmsSheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const filmLikesSheet = ensureSheetWithHeaders_(ss, SHEET_FILM_LIKES, FILM_LIKE_HEADERS);
  const portalSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const reportsSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL_REPORTS, PORTAL_REPORT_HEADERS);
  const videoUploadsSheet = ensureVideoUploadsSheet_(ss);

  return {
    success: true,
    message: "Cine Arena sheets siap.",
    sheets: [membersSheet.getName(), filmsSheet.getName(), filmLikesSheet.getName(), portalSheet.getName(), reportsSheet.getName(), videoUploadsSheet.getName()]
  };
}

function doGet(e) {
  try {
    setupCineArenaSheets();
    const type = String((e && e.parameter && e.parameter.type) || "").trim();

    if (type === "health") {
      return jsonOutput_(healthCheck_());
    }

    if (type === "setup") {
      return jsonOutput_(setupCineArenaSheets());
    }

    if (type === "portal_posts") {
      return jsonOutput_({
        success: true,
        portal_posts: getActivePortalPosts_()
      });
    }

    const likeUserId = String((e && e.parameter && (e.parameter.user_id || e.parameter.like_user_id)) || "").trim();
    const movies = getApprovedMovies_(likeUserId);
    const categories = getApprovedCategories_(movies);
    return jsonOutput_({
      success: true,
      movies: movies,
      films: movies,
      categories: categories
    });
  } catch (error) {
    return jsonOutput_({
      success: false,
      message: error && error.message ? error.message : "Unhandled server error (GET)."
    });
  }
}

function healthCheck_() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const membersSheet = ensureSheetWithHeaders_(ss, SHEET_MEMBERS, MEMBER_HEADERS);
  removeLegacyMemberAdminNoteColumn_(membersSheet);
  const filmsSheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const filmLikesSheet = ensureSheetWithHeaders_(ss, SHEET_FILM_LIKES, FILM_LIKE_HEADERS);
  const portalSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const reportsSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL_REPORTS, PORTAL_REPORT_HEADERS);
  const videoUploadsSheet = ensureVideoUploadsSheet_(ss);

  const filmCoverFolder = DriveApp.getFolderById(FILM_COVER_FOLDER_ID);
  const filmTrailerFolder = DriveApp.getFolderById(FILM_TRAILER_FOLDER_ID);
  const portalFolder = DriveApp.getFolderById(PORTAL_POSTER_FOLDER_ID);

  const probe = uploadHealthProbe_(portalFolder);

  return {
    success: true,
    message: "Health check OK",
    spreadsheet: {
      id: SPREADSHEET_ID,
      name: ss.getName(),
      sheets: [
        membersSheet.getName(),
        filmsSheet.getName(),
        filmLikesSheet.getName(),
        portalSheet.getName(),
        reportsSheet.getName(),
        videoUploadsSheet.getName()
      ]
    },
    drive: {
      film_cover_folder_id: FILM_COVER_FOLDER_ID,
      film_cover_folder_name: filmCoverFolder.getName(),
      film_trailer_folder_id: FILM_TRAILER_FOLDER_ID,
      film_trailer_folder_name: filmTrailerFolder.getName(),
      portal_folder_id: PORTAL_POSTER_FOLDER_ID,
      portal_folder_name: portalFolder.getName()
    },
    upload_probe: probe
  };
}

function uploadHealthProbe_(folder) {
  const fileName = "health_probe_" + Date.now() + ".txt";
  const blob = Utilities.newBlob(
    "CINE ARENA HEALTH CHECK " + new Date().toISOString(),
    "text/plain",
    fileName
  );
  const file = folder.createFile(blob);
  file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  const viewUrl = "https://drive.google.com/uc?export=view&id=" + file.getId();
  return {
    file_id: file.getId(),
    file_name: file.getName(),
    view_url: viewUrl
  };
}

function doPost(e) {
  try {
    setupCineArenaSheets();
    const payload = JSON.parse((e && e.postData && e.postData.contents) || "{}");
    const type = String(payload.type || "").trim();

    if (type === "hub_register" || type === "portal_register") {
      return handleMemberRegister_(payload);
    }
    if (type === "hub_login") {
      return handleMemberLogin_(payload);
    }
    if (type === "hub_check_status") {
      return handleMemberCheckStatus_(payload);
    }
    if (type === "video_upload") {
      return handleVideoUpload_(payload);
    }
    if (type === "portal_post") {
      return handlePortalPost_(payload);
    }
    if (type === "portal_report") {
      return handlePortalReport_(payload);
    }
    if (type === "content_report") {
      return handleContentReport_(payload);
    }
    if (type === "film_like") {
      return handleFilmLike_(payload);
    }
    if (type === "film_view") {
      return handleFilmView_(payload);
    }
    if (type === "film_registration" || type === "song_registration") {
      return handleFilmRegistration_(payload);
    }

    return jsonOutput_({ success: false, message: "Tipe request tidak dikenal." });
  } catch (error) {
    return jsonOutput_({
      success: false,
      message: error && error.message ? error.message : "Unhandled server error."
    });
  }
}

function handleMemberRegister_(payload) {
  const user = registerOrLoginMember_(payload);
  return jsonOutput_({
    success: true,
    message: "Pendaftaran terkirim. Tunggu approval admin.",
    user: user
  });
}

function handleMemberLogin_(payload) {
  const whatsapp = normalizeWhatsapp(payload.whatsapp);
  const user = findMemberByWhatsapp_(whatsapp);
  if (!user) return jsonOutput_({ success: false, message: "Nomor belum terdaftar." });

  const status = normalizeMemberStatus_(user.status);
  if (status === "PENDING") return jsonOutput_({ success: false, message: "Registrasi Anda masih menunggu approval admin." });
  if (status === "BLOCKED") return jsonOutput_({ success: false, message: "Akun Anda diblokir admin." });

  return jsonOutput_({ success: true, user: user });
}

function handleMemberCheckStatus_(payload) {
  const whatsapp = normalizeWhatsapp(payload.whatsapp);
  const user = findMemberByWhatsapp_(whatsapp);
  if (!user) return jsonOutput_({ success: false, message: "Info akun Cine Arena tidak ditemukan." });
  return jsonOutput_({ success: true, message: "Status ditemukan.", user: user });
}

function handleVideoUpload_(payload) {
  const judulVideo = String(payload.judul_video || "").trim();
  const genre = String(payload.genre || "").trim();
  const asalSekolah = String(payload.asal_sekolah || "").trim();
  const whatsapp = normalizeWhatsapp(payload.whatsapp);
  const videoUrl = String(payload.video_url || "").trim();
  if (!judulVideo || !genre || !asalSekolah || !videoUrl) {
    return jsonOutput_({ success: false, message: "Judul video, genre, asal sekolah, dan link video wajib diisi." });
  }

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureVideoUploadsSheet_(ss);
  const now = new Date().toISOString();
  const id = "video_" + Date.now() + "_" + Math.floor(Math.random() * 100000);
  sheet.appendRow([id, judulVideo, genre, asalSekolah, whatsapp, videoUrl, "PENDING", now]);
  return jsonOutput_({ success: true, message: "Upload video berhasil dikirim.", id: id, status: "PENDING" });
}

function ensureVideoUploadsSheet_(ss) {
  const sheet = ss.getSheetByName(SHEET_VIDEO_UPLOADS);
  if (sheet && sheet.getLastColumn() >= 7) {
    const headers = sheet.getRange(1, 1, 1, Math.min(sheet.getLastColumn(), 8)).getValues()[0]
      .map(function(header) { return String(header || "").trim(); });
    const legacyHeaders = ["id", "judul_video", "asal_sekolah", "whatsapp", "video_url", "status", "createdAt"];
    if (legacyHeaders.every(function(header, index) { return headers[index] === header; })) {
      sheet.insertColumnBefore(3);
    }
  }
  return ensureSheetWithHeaders_(ss, SHEET_VIDEO_UPLOADS, VIDEO_UPLOAD_HEADERS);
}

function handleFilmRegistration_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);

  let coverUrl = String(payload.cover_url || payload.poster_url || payload.coverArtworkLink || payload.posterUrl || "").trim();
  if (payload.coverImageBase64) {
    coverUrl = uploadBase64ImageToDrive_(
      payload.coverImageBase64,
      payload.coverImageFileName || "cover.jpg",
      payload.coverImageMimeType || "image/jpeg",
      FILM_COVER_FOLDER_ID
    );
  }

  let movieUrl = String(payload.movie_url || payload.mp3Link || payload.videoUrl || payload.video_url || payload.mediaUrl || "").trim();
  let trailerUrl = String(payload.trailer_url || payload.trailerUrl || "").trim();
  if (payload.trailerFileBase64) {
    trailerUrl = uploadBase64FileToDrive_(
      payload.trailerFileBase64,
      payload.trailerFileName || "trailer.mp4",
      payload.trailerFileMimeType || "video/mp4",
      FILM_TRAILER_FOLDER_ID
    );
    if (!movieUrl) movieUrl = trailerUrl;
  }
  const judulFilm = String(payload.judul_film || payload.title || payload.songTitle || "").trim();
  const release = String(payload.release || payload.tahun_release || payload.releaseYear || payload.tahun_pembuatan || payload.genre || "").trim();
  const category = String(payload.category || "").trim();
  const duration = String(payload.duration || "").trim();
  const director = String(payload.director || payload.bandName || payload.creator || "").trim();
  const namaPemain = String(payload.nama_pemain || payload.castName || payload.cast || "").trim();
  const synopsis = String(payload.synopsis || payload.description || "").trim();

  sheet.appendRow([
    "movie_" + Date.now(),
    judulFilm,
    release,
    category,
    duration,
    director,
    namaPemain,
    synopsis,
    coverUrl,
    trailerUrl,
    movieUrl,
    "pending",
    0,
    0
  ]);

  return jsonOutput_({ success: true, message: "Registrasi film berhasil disimpan. Tunggu review admin." });
}

function handlePortalPost_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const user = validateActiveMember_(payload);
  assertPortalSubmitCooldown_(sheet, user);
  const posterBase64 = String(
    payload.posterImageBase64 ||
    payload.imageBase64 ||
    payload.base64 ||
    payload.fileBase64 ||
    ""
  ).trim();
  if (!posterBase64) throw new Error("Base64 poster tidak ditemukan di payload.");

  const posterUrl = uploadBase64ImageToDrive_(
    posterBase64,
    payload.posterImageFileName || payload.fileName || "poster.jpg",
    payload.posterImageMimeType || payload.mimeType || "image/jpeg",
    PORTAL_POSTER_FOLDER_ID
  );
  const category = String(payload.category || "").trim();

  try {
    sheet.appendRow([
      "portal_" + Date.now(),
      user.id,
      user.bandName,
      user.whatsapp,
      category,
      posterUrl,
      new Date(),
      "ACTIVE"
    ]);
  } catch (error) {
    const detail = error && error.message ? error.message : String(error);
    throw new Error("Upload sukses, tapi gagal tulis ke sheet poster_posts: " + detail);
  }

  return jsonOutput_({ success: true, message: "Poster berhasil dipasang." });
}

function assertPortalSubmitCooldown_(sheet, user) {
  const rows = readSheetObjects_(sheet);
  if (!rows.length) return;

  const userId = String(user.id || "").trim();
  const whatsapp = normalizeWhatsapp(user.whatsapp || "");
  let latestPostingMs = 0;

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    const rowUserId = String(row.user_id || "").trim();
    let rowWhatsapp = "";
    try {
      rowWhatsapp = normalizeWhatsapp(String(row.whatsapp || "").trim());
    } catch (error) {}
    const sameUser = rowUserId === userId || (rowWhatsapp && rowWhatsapp === whatsapp);
    if (!sameUser) continue;
    const postMs = parseDateValue_(row.posting_at);
    if (postMs > latestPostingMs) latestPostingMs = postMs;
  }

  if (!latestPostingMs) return;
  const cooldownMs = PORTAL_POST_SUBMIT_COOLDOWN_MINUTES * 60 * 1000;
  const nowMs = Date.now();
  const remainingMs = latestPostingMs + cooldownMs - nowMs;
  if (remainingMs > 0) {
    const remainingMinutes = Math.ceil(remainingMs / 60000);
    throw new Error("Cooldown kirim poster aktif. Coba lagi " + remainingMinutes + " menit.");
  }
}

function handlePortalReport_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL_REPORTS, PORTAL_REPORT_HEADERS);
  const reason = String(payload.reason || "").trim();
  const postId = String(payload.postId || "").trim();
  const postTitle = String(payload.postTitle || payload.title || "").trim();
  const category = String(payload.category || "").trim();
  const reportedLink = String(payload.reportedLink || payload.reportedDriveLink || "").trim();
  const posterUrl = String(payload.posterUrl || payload.poster_url || getPortalPosterUrlById_(postId) || "").trim();

  if (!postId) return jsonOutput_({ success: false, message: "postId wajib diisi." });
  if (!reason) return jsonOutput_({ success: false, message: "reason wajib diisi." });

  sheet.appendRow([
    "report_" + Date.now(),
    postId,
    postTitle,
    category,
    reason,
    reportedLink,
    posterUrl,
    "NEW"
  ]);

  return jsonOutput_({ success: true, message: "Laporan diterima. Terima kasih." });
}

function handleContentReport_(payload) {
  const targetType = String(payload.targetType || "").trim().toLowerCase();
  const targetId = String(payload.targetId || "").trim();
  const reason = String(payload.reason || "").trim();
  const reportedLink = String(payload.reportedLink || payload.reportedDriveLink || "").trim();
  const posterUrl = String(payload.posterUrl || payload.poster_url || (targetType === "poster" ? getPortalPosterUrlById_(targetId) : "") || "").trim();

  if (!targetId) return jsonOutput_({ success: false, message: "targetId wajib diisi." });
  if (!reason) return jsonOutput_({ success: false, message: "reason wajib diisi." });
  if (targetType !== "film" && targetType !== "poster") {
    return jsonOutput_({ success: false, message: "targetType harus film atau poster." });
  }

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const reportsSheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL_REPORTS, PORTAL_REPORT_HEADERS);
  reportsSheet.appendRow([
    "report_" + Date.now(),
    targetId,
    String(payload.postTitle || payload.title || "").trim(),
    String(payload.category || targetType || "").trim(),
    "[" + targetType + "] " + reason,
    reportedLink,
    posterUrl,
    "NEW"
  ]);

  return jsonOutput_({ success: true, message: "Laporan diterima." });
}

function handleFilmLike_(payload) {
  const filmId = String(payload.film_id || payload.movie_id || payload.id || "").trim();
  if (!filmId) return jsonOutput_({ success: false, message: "film_id wajib diisi." });
  const user = validateActiveMember_(payload);

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const filmsSheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const likesSheet = ensureSheetWithHeaders_(ss, SHEET_FILM_LIKES, FILM_LIKE_HEADERS);
  if (!findFilmById_(filmsSheet, filmId)) {
    return jsonOutput_({ success: false, message: "Film tidak ditemukan." });
  }

  if (!hasFilmLike_(likesSheet, filmId, user.id)) {
    likesSheet.appendRow([filmId, user.id, new Date()]);
  }

  const totalLikes = countFilmLikes_(likesSheet, filmId);
  updateFilmLikesColumn_(filmsSheet, filmId, totalLikes);
  return jsonOutput_({
    success: true,
    already_liked: true,
    total_likes: totalLikes,
    likes: totalLikes
  });
}

function handleFilmView_(payload) {
  const filmId = String(payload.film_id || payload.movie_id || payload.id || "").trim();
  const movieUrl = String(payload.movie_url || payload.video_url || payload.videoUrl || "").trim();
  if (!filmId && !movieUrl) {
    return jsonOutput_({ success: false, message: "film_id atau movie_url wajib diisi." });
  }

  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return jsonOutput_({ success: false, message: "Data film kosong." });

  const col = getSheetHeaderMap_(values[0]);
  const idIdx = col["id"];
  const movieUrlIdx = col["movie_url"];
  const viewsIdx = col["views"];
  if (viewsIdx === undefined) {
    return jsonOutput_({ success: false, message: "Kolom views tidak ditemukan." });
  }

  const payloadMovieFileId = extractDriveFileIdFromUrl_(movieUrl);
  for (let r = 1; r < values.length; r++) {
    const rowId = idIdx !== undefined ? String(values[r][idIdx] || "").trim() : "";
    const rowMovieUrl = movieUrlIdx !== undefined ? String(values[r][movieUrlIdx] || "").trim() : "";
    const rowMovieFileId = extractDriveFileIdFromUrl_(rowMovieUrl);
    const matchedById = filmId && rowId === filmId;
    const matchedByMovieUrl = movieUrl && (rowMovieUrl === movieUrl || (payloadMovieFileId && payloadMovieFileId === rowMovieFileId));
    if (!matchedById && !matchedByMovieUrl) continue;

    const current = Number(values[r][viewsIdx] || 0);
    const next = Math.max(0, current + 1);
    sheet.getRange(r + 1, viewsIdx + 1).setValue(next);
    return jsonOutput_({ success: true, views: next });
  }

  return jsonOutput_({ success: false, message: "Film tidak ditemukan." });
}

function getPortalPosterUrlById_(postId) {
  const id = String(postId || "").trim();
  if (!id) return "";
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  const rows = readSheetObjects_(sheet);
  for (let i = 0; i < rows.length; i++) {
    const rowId = String(rows[i].id || "").trim();
    if (rowId === id) return String(rows[i].poster_url || "").trim();
  }
  return "";
}

function diagnoseDriveAccess() {
  try {
    const folder = DriveApp.getFolderById(PORTAL_POSTER_FOLDER_ID);
    return {
      success: true,
      message: "DriveApp OK",
      folderName: folder.getName(),
      folderId: PORTAL_POSTER_FOLDER_ID
    };
  } catch (error) {
    return {
      success: false,
      message: "DriveApp belum punya izin. Jalankan manual function diagnoseDriveAccess() lalu Authorize, kemudian deploy ulang Web App as Execute as: Me.",
      detail: error && error.message ? error.message : String(error)
    };
  }
}

function getApprovedMovies_(likeUserId) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_FILMS, FILM_HEADERS);
  const likesSheet = ensureSheetWithHeaders_(ss, SHEET_FILM_LIKES, FILM_LIKE_HEADERS);
  const likeSummary = getFilmLikesSummary_(likesSheet, likeUserId);
  return readSheetObjects_(sheet)
    .filter(function(movie) {
      return String(movie.status || "").trim().toLowerCase() === "approved";
    })
    .map(function(movie) {
      const movieId = String(movie.id || "").trim();
      const countedLikes = likeSummary.totalByVideoId[movieId];
      const totalLikes = countedLikes === undefined ? Number(movie.likes || 0) : countedLikes;
      return {
        id: movieId,
        film_id: movieId,
        title: String(movie.judul_film || movie.title || "").trim(),
        genre: String(movie.category || movie.genre || "").trim(),
        judul_film: String(movie.judul_film || movie.title || "").trim(),
        release: String(movie.release || movie.tahun_release || movie.tahun_pembuatan || "").trim(),
        category: String(movie.category || "").trim(),
        duration: String(movie.duration || "").trim(),
        director: String(movie.director || "").trim(),
        nama_pemain: String(movie.nama_pemain || "").trim(),
        synopsis: String(movie.synopsis || "").trim(),
        description: String(movie.synopsis || "").trim(),
        cover_url: String(movie.cover_url || movie.poster_url || "").trim(),
        poster_url: String(movie.cover_url || movie.poster_url || "").trim(),
        trailer_url: String(movie.trailer_url || "").trim(),
        movie_url: String(movie.movie_url || "").trim(),
        video_url: String(movie.movie_url || "").trim(),
        status: String(movie.status || "").trim(),
        views: Number(movie.views || 0),
        likes: totalLikes,
        total_likes: totalLikes,
        already_liked: !!likeSummary.likedByVideoId[movieId]
      };
    });
}

function getFilmLikesSummary_(likesSheet, likeUserId) {
  const rows = readSheetObjects_(likesSheet);
  const totalByVideoId = {};
  const likedByVideoId = {};
  const activeUserId = String(likeUserId || "").trim();

  rows.forEach(function(row) {
    const videoId = String(row.video_id || "").trim();
    const userId = String(row.user_id || "").trim();
    if (!videoId || !userId) return;
    totalByVideoId[videoId] = (totalByVideoId[videoId] || 0) + 1;
    if (activeUserId && userId === activeUserId) likedByVideoId[videoId] = true;
  });

  return {
    totalByVideoId: totalByVideoId,
    likedByVideoId: likedByVideoId
  };
}

function hasFilmLike_(likesSheet, videoId, userId) {
  const rows = readSheetObjects_(likesSheet);
  const targetVideoId = String(videoId || "").trim();
  const targetUserId = String(userId || "").trim();
  return rows.some(function(row) {
    return String(row.video_id || "").trim() === targetVideoId &&
      String(row.user_id || "").trim() === targetUserId;
  });
}

function countFilmLikes_(likesSheet, videoId) {
  const rows = readSheetObjects_(likesSheet);
  const targetVideoId = String(videoId || "").trim();
  return rows.filter(function(row) {
    return String(row.video_id || "").trim() === targetVideoId &&
      String(row.user_id || "").trim();
  }).length;
}

function findFilmById_(filmsSheet, filmId) {
  const values = filmsSheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);
  const idIdx = col["id"];
  if (idIdx === undefined) return null;
  for (let r = 1; r < values.length; r++) {
    if (String(values[r][idIdx] || "").trim() === filmId) {
      return { rowNumber: r + 1, row: values[r], col: col };
    }
  }
  return null;
}

function updateFilmLikesColumn_(filmsSheet, filmId, likes) {
  const found = findFilmById_(filmsSheet, filmId);
  if (!found) return;
  const likesIdx = found.col["likes"];
  if (likesIdx === undefined) return;
  filmsSheet.getRange(found.rowNumber, likesIdx + 1).setValue(Math.max(0, Number(likes || 0)));
}

function getApprovedCategories_(movies) {
  const set = {};
  movies.forEach(function(movie) {
    const category = String(movie.category || "").trim();
    if (!category) return;
    set[category] = true;
  });
  return Object.keys(set).sort();
}

function getActivePortalPosts_() {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_PORTAL, PORTAL_HEADERS);
  expirePortalPostsIfNeeded_(sheet);
  return readSheetObjects_(sheet)
    .filter(function(post) {
      return String(post.status || "").trim().toUpperCase() === "ACTIVE";
    })
    .map(function(post) {
      const creatorName = getPortalCreatorName_(post);
      return {
        id: post.id,
        title: post.category || creatorName || "",
        band_name: creatorName,
        creator_name: creatorName,
        submitter_name: creatorName,
        category: post.category || "",
        poster_url: post.poster_url || "",
        short_caption: "",
        caption: "",
        event_date: "",
        posting_at: formatDateValue_(post.posting_at),
        status: post.status || "",
        created_at: formatDateValue_(post.posting_at)
      };
    });
}

function expirePortalPostsIfNeeded_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return;
  const col = getSheetHeaderMap_(values[0]);
  const statusIdx = col["status"];
  const postingAtIdx = col["posting_at"];
  const posterUrlIdx = col["poster_url"];
  if (statusIdx === undefined || postingAtIdx === undefined) return;

  const nowMs = Date.now();
  const expireMs = PORTAL_POST_EXPIRE_HOURS * 60 * 60 * 1000;
  for (let r = 1; r < values.length; r++) {
    const status = String(values[r][statusIdx] || "").trim().toUpperCase();
    if (status !== "ACTIVE") continue;
    const postingMs = parseDateValue_(values[r][postingAtIdx]);
    if (!postingMs) continue;
    if (nowMs - postingMs >= expireMs) {
      if (posterUrlIdx !== undefined) {
        const posterUrl = String(values[r][posterUrlIdx] || "").trim();
        deletePosterFileByUrl_(posterUrl);
      }
      sheet.getRange(r + 1, statusIdx + 1).setValue("EXPIRED");
    }
  }
}

function deletePosterFileByUrl_(url) {
  const fileId = extractDriveFileIdFromUrl_(url);
  if (!fileId) return;
  try {
    const file = DriveApp.getFileById(fileId);
    file.setTrashed(true);
  } catch (error) {
    Logger.log("Gagal hapus file expired: " + (error && error.message ? error.message : String(error)));
  }
}

function extractDriveFileIdFromUrl_(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";

  const byIdParam = raw.match(/[?&]id=([a-zA-Z0-9_-]{10,})/);
  if (byIdParam && byIdParam[1]) return byIdParam[1];

  const byFilePath = raw.match(/\/file\/d\/([a-zA-Z0-9_-]{10,})/);
  if (byFilePath && byFilePath[1]) return byFilePath[1];

  const byUcPath = raw.match(/\/uc\?[^#]*id=([a-zA-Z0-9_-]{10,})/);
  if (byUcPath && byUcPath[1]) return byUcPath[1];

  return "";
}

function getPortalCreatorName_(post) {
  return String(post.creator_name || post.band_name || "").trim();
}

function getPortalCaption_(post) {
  return String(post.caption || post.short_caption || "").trim();
}

function registerOrLoginMember_(payload) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_MEMBERS, MEMBER_HEADERS);
  const whatsapp = normalizeWhatsapp(payload.whatsapp);
  const existing = findMemberByWhatsapp_(whatsapp);
  if (existing) {
    if (normalizeMemberStatus_(existing.status) === "BLOCKED") throw new Error("Akun Anda diblokir admin.");
    return existing;
  }

  const createdAt = new Date();
  const id = "member_" + whatsapp;
  const namaKreator = String(payload.nama_kreator || payload.bandName || payload.creator || "").trim();
  const asalSekolah = String(payload.asal_sekolah || payload.asalSekolah || payload.school || payload.city || "").trim();
  sheet.appendRow([
    id,
    namaKreator,
    asalSekolah,
    whatsapp,
    "PENDING",
    createdAt,
    "creator"
  ]);

  return {
    id: id,
    bandName: namaKreator,
    genre: asalSekolah,
    city: asalSekolah,
    whatsapp: whatsapp,
    createdAt: formatDateValue_(createdAt),
    status: "PENDING"
  };
}

function validateActiveMember_(payload) {
  const submittedUserId = String(payload.userId || "").trim();
  const submittedWhatsapp = normalizeWhatsapp(payload.whatsapp);
  let user = submittedUserId ? findMemberById_(submittedUserId) : null;
  if (!user) user = findMemberByWhatsapp_(submittedWhatsapp);

  if (!user) throw new Error("User Cine Arena tidak ditemukan.");
  if (normalizeMemberStatus_(user.status) === "BLOCKED") throw new Error("Akun Anda diblokir admin.");
  if (normalizeMemberStatus_(user.status) !== "ACTIVE") throw new Error("Akun Cine Arena belum aktif.");
  if (normalizeWhatsapp(user.whatsapp) !== submittedWhatsapp) throw new Error("Data WhatsApp tidak cocok dengan akun Cine Arena.");

  return {
    id: String(user.id || "").trim(),
    bandName: String(user.bandName || "").trim(),
    whatsapp: submittedWhatsapp
  };
}

function findMemberByWhatsapp_(number) {
  const normalized = normalizeWhatsapp(number);
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_MEMBERS, MEMBER_HEADERS);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);

  for (let rowIndex = 1; rowIndex < values.length; rowIndex++) {
    const row = values[rowIndex];
    try {
      if (normalizeWhatsapp(getRowValueByHeader_(row, col, "whatsapp") || "") === normalized) {
        return mapMemberFromSheetRow_(sheet, row, rowIndex + 1, col);
      }
    } catch (error) {}
  }
  return null;
}

function findMemberById_(id) {
  const submittedId = String(id || "").trim();
  if (!submittedId) return null;
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ensureSheetWithHeaders_(ss, SHEET_MEMBERS, MEMBER_HEADERS);
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return null;
  const col = getSheetHeaderMap_(values[0]);

  for (let rowIndex = 1; rowIndex < values.length; rowIndex++) {
    const row = values[rowIndex];
    const rowId = getOrRepairMemberId_(sheet, row, rowIndex + 1, col);
    if (String(rowId) === submittedId) {
      return mapMemberFromSheetRow_(sheet, row, rowIndex + 1, col);
    }
  }
  return null;
}

function mapMemberFromSheetRow_(sheet, row, rowNumber, col) {
  const repairedId = getOrRepairMemberId_(sheet, row, rowNumber, col);
  const whatsapp = normalizeWhatsapp(getRowValueByHeader_(row, col, "whatsapp") || "");
  const createdAt = getOrRepairMemberCreatedAt_(sheet, row, rowNumber, col);
  const namaKreator = String(getRowValueByHeader_(row, col, "nama_kreator") || getRowValueByHeader_(row, col, "bandName") || "").trim();
  const asalSekolah = String(getRowValueByHeader_(row, col, "asal_sekolah") || getRowValueByHeader_(row, col, "city") || "").trim();
  return {
    id: repairedId,
    bandName: namaKreator,
    genre: asalSekolah,
    city: asalSekolah,
    whatsapp: whatsapp,
    createdAt: formatDateValue_(createdAt),
    status: normalizeMemberStatus_(getRowValueByHeader_(row, col, "status"))
  };
}

function normalizeWhatsapp(number) {
  const compact = String(number || "")
    .replace(/\s+/g, "")
    .replace(/-/g, "")
    .replace(/[()]/g, "")
    .replace(/\+/g, "")
    .trim();

  let normalized = "";
  if (compact.indexOf("0") === 0) normalized = "62" + compact.substring(1);
  else if (compact.indexOf("8") === 0) normalized = "62" + compact;
  else if (compact.indexOf("62") === 0) normalized = compact;
  else throw new Error("Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif.");

  if (!/^\d+$/.test(normalized) || normalized.length < 10 || normalized.length > 15) {
    throw new Error("Nomor WhatsApp tidak valid. Gunakan nomor Indonesia aktif.");
  }
  return normalized;
}

function normalizeMemberStatus_(value) {
  const status = String(value || "").trim().toUpperCase();
  if (status === "ACTIVE") return "ACTIVE";
  if (status === "BLOCKED") return "BLOCKED";
  return "PENDING";
}

function uploadBase64ImageToDrive_(base64, fileName, mimeType, folderId) {
  try {
    const folder = DriveApp.getFolderById(folderId);
    const cleanBase64 = String(base64 || "").replace(/^data:.*;base64,/, "");
    const bytes = Utilities.base64Decode(cleanBase64);
    const blob = Utilities.newBlob(bytes, mimeType, fileName);
    const file = folder.createFile(blob);
    trySetPublicView_(file);
    return buildDriveImageUrl_(file.getId());
  } catch (error) {
    const detail = error && error.message ? error.message : String(error);
    Logger.log("Drive upload failed: " + detail);
    throw new Error("Upload poster ke Drive gagal: " + detail);
  }
}

function buildDriveImageUrl_(fileId) {
  const id = String(fileId || "").trim();
  if (!id) return "";
  return "https://drive.google.com/thumbnail?id=" + id + "&sz=w2000";
}

function uploadBase64FileToDrive_(base64, fileName, mimeType, folderId) {
  try {
    const folder = DriveApp.getFolderById(folderId);
    const cleanBase64 = String(base64 || "").replace(/^data:.*;base64,/, "");
    const bytes = Utilities.base64Decode(cleanBase64);
    const blob = Utilities.newBlob(bytes, mimeType, fileName);
    const file = folder.createFile(blob);
    trySetPublicView_(file);
    return "https://drive.google.com/uc?export=view&id=" + file.getId();
  } catch (error) {
    Logger.log("Drive file upload failed: " + (error && error.message ? error.message : String(error)));
    throw new Error("Upload trailer ke Drive gagal.");
  }
}

function trySetPublicView_(file) {
  try {
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  } catch (error) {
    Logger.log("Drive sharing skipped: " + (error && error.message ? error.message : String(error)));
  }
}

function getSheetHeaderMap_(headersRow) {
  const col = {};
  headersRow.forEach(function(header, index) {
    col[String(header || "").trim()] = index;
  });
  return col;
}

function getRowValueByHeader_(row, col, headerName) {
  if (!Object.prototype.hasOwnProperty.call(col, headerName)) return "";
  return row[col[headerName]];
}

function getOrRepairMemberId_(sheet, row, rowNumber, col) {
  const idColumnIndex = col["id"];
  if (idColumnIndex === undefined) throw new Error("Kolom id tidak ditemukan di members.");
  const rawId = String(row[idColumnIndex] || "").trim();
  if (rawId) return rawId;
  const whatsapp = normalizeWhatsapp(getRowValueByHeader_(row, col, "whatsapp") || "");
  const repairedId = "member_" + whatsapp;
  sheet.getRange(rowNumber, idColumnIndex + 1).setValue(repairedId);
  row[idColumnIndex] = repairedId;
  return repairedId;
}

function getOrRepairMemberCreatedAt_(sheet, row, rowNumber, col) {
  const createdAtColumnIndex = col["createdAt"];
  if (createdAtColumnIndex === undefined) throw new Error("Kolom createdAt tidak ditemukan di members.");
  const rawCreatedAt = row[createdAtColumnIndex];
  if (rawCreatedAt) return rawCreatedAt;
  const repairedCreatedAt = new Date();
  sheet.getRange(rowNumber, createdAtColumnIndex + 1).setValue(repairedCreatedAt);
  row[createdAtColumnIndex] = repairedCreatedAt;
  return repairedCreatedAt;
}

function ensureSheetWithHeaders_(ss, sheetName, headers) {
  const sheet = ss.getSheetByName(sheetName) || ss.insertSheet(sheetName);
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headers);
  } else {
    const currentHeaders = sheet.getRange(1, 1, 1, headers.length).getValues()[0];
    const mismatch = headers.some(function(header, index) {
      return String(currentHeaders[index] || "").trim() !== header;
    });
    if (mismatch) {
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
    }
  }
  return sheet;
}

function removeLegacyMemberAdminNoteColumn_(membersSheet) {
  const maxColumns = membersSheet.getMaxColumns();
  const headerRow = membersSheet.getRange(1, 1, 1, maxColumns).getValues()[0];
  for (let i = 0; i < headerRow.length; i++) {
    if (String(headerRow[i] || "").trim() === "admin_note") {
      membersSheet.deleteColumn(i + 1);
      break;
    }
  }
}

function readSheetObjects_(sheet) {
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return [];
  const headers = values[0].map(function(header) { return String(header).trim(); });
  return values.slice(1).map(function(row) {
    const item = {};
    headers.forEach(function(header, index) { item[header] = row[index]; });
    return item;
  });
}

function parseDateValue_(value) {
  if (!value) return 0;
  if (Object.prototype.toString.call(value) === "[object Date]") return value.getTime();
  const parsed = new Date(value);
  return isNaN(parsed.getTime()) ? 0 : parsed.getTime();
}

function formatDateValue_(value) {
  const timestamp = parseDateValue_(value);
  if (!timestamp) return "";
  return Utilities.formatDate(new Date(timestamp), "Asia/Jakarta", "yyyy-MM-dd'T'HH:mm:ssXXX");
}

function jsonOutput_(payload) {
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

function incrementReportCount_(sheetName, idHeader, targetId, countHeader) {
  const ss = SpreadsheetApp.openById(SPREADSHEET_ID);
  const sheet = ss.getSheetByName(sheetName);
  if (!sheet) return;
  const values = sheet.getDataRange().getValues();
  if (values.length <= 1) return;
  const col = getSheetHeaderMap_(values[0]);
  const idIdx = col[idHeader];
  const countIdx = col[countHeader];
  if (idIdx === undefined || countIdx === undefined) return;

  for (let r = 1; r < values.length; r++) {
    const rowId = String(values[r][idIdx] || "").trim();
    if (rowId === targetId) {
      const current = Number(values[r][countIdx] || 0);
      sheet.getRange(r + 1, countIdx + 1).setValue(current + 1);
      break;
    }
  }
}
