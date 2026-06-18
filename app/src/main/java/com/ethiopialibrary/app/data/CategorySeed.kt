package com.ethiopialibrary.app.data

/**
 * The library's starting categories (name + 2-letter code), seeded on first run.
 * Staff can add more later. Order here is the display order.
 */
object CategorySeed {
    val entries: List<Pair<String, String>> = listOf(
        "علوم القران" to "QN",
        "التفسير" to "TF",
        "العقيدة" to "AQ",
        "علوم الحديث" to "HD",
        "الفقه وأصوله" to "FQ",
        "السيرة والتاريخ" to "SR",
        "اللغة العربية" to "AR",
        "البحوث والمسائل" to "BR",
        "الفتاوى" to "FT",
        "الرقائق والأداب والأذكار" to "AD",
        "الدعوة وأحوال المسلمين" to "DW",
        "كتب الأطفال" to "KD",
        "علوم أخرى" to "OT",
    )
}
