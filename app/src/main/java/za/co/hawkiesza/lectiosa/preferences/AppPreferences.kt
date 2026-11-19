package za.co.hawkiesza.lectiosa.preferences

enum class AppThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AppTextSize {
    DEFAULT,
    LARGE,
    EXTRA_LARGE,
}

data class AppPreferences(
    val theme: AppThemePreference = AppThemePreference.SYSTEM,
    val textSize: AppTextSize = AppTextSize.DEFAULT,
    val showVerseNumbers: Boolean = false,
    val rememberedDate: String? = null,
    val rememberedMassFormId: String? = null,
)
