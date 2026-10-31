package com.stockfileexplorer.app

import android.content.Context

/** User-selectable file actions shown in the long-press context menu. */
enum class FileMenuAction(val labelRes: Int, val filesOnly: Boolean = false) {
    OPEN(R.string.menu_open),
    SAVE_LOCATION(R.string.save_location),
    CONTAINING_FOLDER(R.string.open_containing_folder, filesOnly = true),
    COPY(R.string.copy),
    MOVE(R.string.move_to),
    RENAME(R.string.rename),
    PROPERTIES(R.string.properties),
    FAVORITE(R.string.favorite),
    SHARE(R.string.share, filesOnly = true),
    OPEN_WITH(R.string.open_with, filesOnly = true),
    DELETE(R.string.delete),
    SELECT(R.string.select),
}

object FileActionPreferences {
    private const val PREFS = "file-action-preferences"
    private const val VISIBLE_ACTIONS = "visible-menu-actions"
    private const val FAVORITES = "favorite-paths"

    fun visibleActions(context: Context): Set<FileMenuAction> {
        val stored = preferences(context).getStringSet(VISIBLE_ACTIONS, null)
        if (stored == null) return FileMenuAction.entries.toSet()
        val actions = stored.mapNotNull { name -> FileMenuAction.entries.firstOrNull { it.name == name } }.toSet()
        if (!preferences(context).getBoolean("location-actions-added", false)) {
            val updated = actions + FileMenuAction.CONTAINING_FOLDER + FileMenuAction.SAVE_LOCATION
            preferences(context).edit().putStringSet(VISIBLE_ACTIONS, updated.mapTo(mutableSetOf()) { it.name })
                .putBoolean("location-actions-added", true).apply()
            return updated
        }
        return actions
    }

    fun setVisibleActions(context: Context, actions: Set<FileMenuAction>) {
        preferences(context).edit().putBoolean("location-actions-added", true)
            .putStringSet(VISIBLE_ACTIONS, actions.mapTo(mutableSetOf()) { it.name })
            .apply()
    }

    fun favoritePaths(context: Context): Set<String> =
        preferences(context).getStringSet(FAVORITES, emptySet()).orEmpty()

    fun isFavorite(context: Context, path: String): Boolean = path in favoritePaths(context)

    fun toggleFavorite(context: Context, path: String): Boolean {
        val favorites = favoritePaths(context).toMutableSet()
        val nowFavorite = favorites.add(path)
        if (!nowFavorite) favorites.remove(path)
        preferences(context).edit().putStringSet(FAVORITES, favorites).apply()
        return nowFavorite
    }

    fun shortcutPaths(context: Context): Set<String> = preferences(context).getStringSet("folder-shortcuts", emptySet()).orEmpty()
    fun saveShortcut(context: Context, path: String) {
        preferences(context).edit().putStringSet("folder-shortcuts", shortcutPaths(context) + path).apply()
    }
    fun removeShortcut(context: Context, path: String) {
        preferences(context).edit().putStringSet("folder-shortcuts", shortcutPaths(context) - path).apply()
    }

    /** Folder-browser sort order, kept across folders and app restarts. Unknown or missing values mean name. */
    fun browserSort(context: Context): String = preferences(context).getString("browser-sort", null) ?: "NAME"
    fun saveBrowserSort(context: Context, mode: String) {
        preferences(context).edit().putString("browser-sort", mode).apply()
    }
    /** Category-list sort as "MODE:ascending", kept per category key; null when never chosen. */
    fun categorySort(context: Context, key: String): String? = preferences(context).getString("category-sort-$key", null)
    fun saveCategorySort(context: Context, key: String, mode: String, ascending: Boolean) {
        preferences(context).edit().putString("category-sort-$key", "$mode:$ascending").apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
