package com.grainmvp.android.session

/** A four-digit year followed by DS (dry season) or WS (wet season), e.g. "2026WS". Same rule as the backend. */
private val SEASON_REGEX = Regex("^\\d{4}(DS|WS)$")

/** Whether [value] is exactly a season code such as "2026WS" or "2026DS". Case-sensitive, no surrounding spaces. */
fun isValidSeason(value: String): Boolean = SEASON_REGEX.matches(value)

/** Trims and upper-cases what the technician typed, so "2026ws " is accepted as "2026WS". */
fun normalizeSeason(raw: String): String = raw.trim().uppercase()
