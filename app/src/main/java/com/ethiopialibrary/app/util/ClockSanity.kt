package com.ethiopialibrary.app.util

/**
 * A device clock reset to before this build even exists (dead battery, no
 * NTP while offline) would permanently corrupt every `dueAt` written from
 * that point on - checkouts and renews are blocked until the operator fixes
 * the date. Returns are never gated by this: refusing them would strand
 * books at the desk instead of preventing any real corruption.
 */
fun clockLooksWrong(now: Long, buildTimeMillis: Long): Boolean = now < buildTimeMillis
