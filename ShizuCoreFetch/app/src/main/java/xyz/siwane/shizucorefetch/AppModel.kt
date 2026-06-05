package xyz.siwane.shizucorefetch

data class AppModel(
    val id: String,
    val name: String,
    val developer: String,
    val description: String,
    val descriptionAr: String = "", // الوصف العربي
    val iconUrl: String, 
    val bannerUrl: String = "",     // البانر الافتراضي
    val bannerUrlAr: String = "",   // البانر العربي
    val downloadUrl: String,
    val hasJsonStore: Boolean = false // هل يمتلك واجهة متجر مخصصة؟
)
