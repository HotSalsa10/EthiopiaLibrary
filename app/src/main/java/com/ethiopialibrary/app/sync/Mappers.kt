package com.ethiopialibrary.app.sync

import com.ethiopialibrary.app.data.BookCopyEntity
import com.ethiopialibrary.app.data.BookEntity
import com.ethiopialibrary.app.data.CategoryEntity
import com.ethiopialibrary.app.data.CopyStatus
import com.ethiopialibrary.app.data.LoanEntity
import com.ethiopialibrary.app.data.MemberEntity
import com.ethiopialibrary.app.data.MemberStatus

// Document ID is the entity's UUID, so re-uploads are idempotent upserts.

internal fun CategoryEntity.toMap(): Map<String, Any?> = mapOf(
    "code" to code,
    "name" to name,
    "sortOrder" to sortOrder,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun categoryFrom(id: String, m: Map<String, Any?>) = CategoryEntity(
    id = id,
    code = m.str("code"),
    name = m.str("name"),
    sortOrder = m.int("sortOrder"),
    createdAt = m.long("createdAt"),
    updatedAt = m.long("updatedAt"),
    isDeleted = m.bool("isDeleted"),
)

internal fun BookEntity.toMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "author" to author,
    "categoryCode" to categoryCode,
    "bookNumber" to bookNumber,
    "language" to language,
    "isbn" to isbn,
    "notes" to notes,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun bookFrom(id: String, m: Map<String, Any?>) = BookEntity(
    id = id,
    title = m.str("title"),
    author = m.str("author"),
    categoryCode = m.str("categoryCode"),
    bookNumber = m.int("bookNumber"),
    language = m.str("language"),
    isbn = m.optStr("isbn"),
    notes = m.optStr("notes"),
    createdAt = m.long("createdAt"),
    updatedAt = m.long("updatedAt"),
    isDeleted = m.bool("isDeleted"),
)

internal fun BookCopyEntity.toMap(): Map<String, Any?> = mapOf(
    "bookId" to bookId,
    "copyCode" to copyCode,
    "copyNumber" to copyNumber,
    "volumeNumber" to volumeNumber,
    "shelfLocation" to shelfLocation,
    "status" to status.name,
    "acquiredAt" to acquiredAt,
    "notes" to notes,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun copyFrom(id: String, m: Map<String, Any?>) = BookCopyEntity(
    id = id,
    bookId = m.str("bookId"),
    copyCode = m.str("copyCode"),
    copyNumber = m.int("copyNumber"),
    volumeNumber = m.int("volumeNumber"),
    shelfLocation = m.optStr("shelfLocation"),
    status = CopyStatus.valueOf(m.str("status")),
    acquiredAt = m.long("acquiredAt"),
    notes = m.optStr("notes"),
    createdAt = m.long("createdAt"),
    updatedAt = m.long("updatedAt"),
    isDeleted = m.bool("isDeleted"),
)

internal fun MemberEntity.toMap(): Map<String, Any?> = mapOf(
    "memberCode" to memberCode,
    "fullName" to fullName,
    "phone" to phone,
    "nationalId" to nationalId,
    "address" to address,
    "joinedAt" to joinedAt,
    "status" to status.name,
    "notes" to notes,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun memberFrom(id: String, m: Map<String, Any?>) = MemberEntity(
    id = id,
    memberCode = m.str("memberCode"),
    fullName = m.str("fullName"),
    phone = m.optStr("phone"),
    nationalId = m.optStr("nationalId"),
    address = m.optStr("address"),
    joinedAt = m.long("joinedAt"),
    status = MemberStatus.valueOf(m.str("status")),
    notes = m.optStr("notes"),
    createdAt = m.long("createdAt"),
    updatedAt = m.long("updatedAt"),
    isDeleted = m.bool("isDeleted"),
)

internal fun LoanEntity.toMap(): Map<String, Any?> = mapOf(
    "copyId" to copyId,
    "memberId" to memberId,
    "borrowedAt" to borrowedAt,
    "dueAt" to dueAt,
    "returnedAt" to returnedAt,
    "rating" to rating,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "isDeleted" to isDeleted,
)

internal fun loanFrom(id: String, m: Map<String, Any?>) = LoanEntity(
    id = id,
    copyId = m.str("copyId"),
    memberId = m.str("memberId"),
    borrowedAt = m.long("borrowedAt"),
    dueAt = m.long("dueAt"),
    returnedAt = m.optLong("returnedAt"),
    rating = m.optInt("rating"),
    createdAt = m.long("createdAt"),
    updatedAt = m.long("updatedAt"),
    isDeleted = m.bool("isDeleted"),
)

private fun Map<String, Any?>.str(key: String): String = this[key] as String
private fun Map<String, Any?>.optStr(key: String): String? = this[key] as String?
private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()
private fun Map<String, Any?>.optInt(key: String): Int? = (this[key] as Number?)?.toInt()
private fun Map<String, Any?>.long(key: String): Long = (this[key] as Number).toLong()
private fun Map<String, Any?>.optLong(key: String): Long? = (this[key] as Number?)?.toLong()
private fun Map<String, Any?>.bool(key: String): Boolean = this[key] as? Boolean ?: false
