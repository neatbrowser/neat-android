package io.github.neatbrowser.search.core

/** A tab always has an address; "no tab selected" is what shows the home screen. */
data class Tab(
    val id: Long,
    val url: String,
    val title: String = "",
    /** Opened in ghost (incognito) mode: kept in memory only and never written to disk. */
    val ghost: Boolean = false,
)

/**
 * All tabs of both modes, and which one is selected in each mode. Immutable: every change returns
 * a new state. Only the tabs of the current mode are visible; switching mode switches workspace.
 */
data class TabsState(
    val tabs: List<Tab> = emptyList(),
    val selectedNormal: Long? = null,
    val selectedGhost: Long? = null,
    val ghostMode: Boolean = false,
    val nextId: Long = 1,
) {
    /** Tabs of the current mode, in order. */
    val visible: List<Tab> get() = tabs.filter { it.ghost == ghostMode }

    /** The selected tab of the current mode, or null (home screen). */
    val selected: Tab? get() {
        val id = (if (ghostMode) selectedGhost else selectedNormal) ?: return null
        return tabs.firstOrNull { it.id == id && it.ghost == ghostMode }
    }

    fun tab(id: Long): Tab? = tabs.firstOrNull { it.id == id }

    /** Adds a tab to the current mode and selects it. */
    fun open(url: String): TabsState {
        val tab = Tab(id = nextId, url = url, ghost = ghostMode)
        return withSelected(tab.ghost, tab.id).copy(tabs = tabs + tab, nextId = nextId + 1)
    }

    /** Selects a tab of the current mode; anything else is ignored. */
    fun select(id: Long): TabsState {
        val tab = tab(id) ?: return this
        if (tab.ghost != ghostMode) return this
        return withSelected(tab.ghost, id)
    }

    /**
     * Removes a tab. When it was the selected one the tab that takes its place (the next one, or the
     * previous one when it was last) is selected; with no tab left, the home screen shows.
     */
    fun close(id: Long): TabsState {
        val tab = tab(id) ?: return this
        val sameMode = tabs.filter { it.ghost == tab.ghost }
        val index = sameMode.indexOfFirst { it.id == id }
        val remaining = tabs.filter { it.id != id }
        val wasSelected = (if (tab.ghost) selectedGhost else selectedNormal) == id
        val next = if (wasSelected) {
            val rest = sameMode.filter { it.id != id }
            rest.getOrNull(index)?.id ?: rest.lastOrNull()?.id
        } else {
            if (tab.ghost) selectedGhost else selectedNormal
        }
        return withSelected(tab.ghost, next).copy(tabs = remaining)
    }

    /** Address or title of a tab changed. */
    fun update(id: Long, url: String? = null, title: String? = null): TabsState {
        if (tabs.none { it.id == id }) return this
        return copy(tabs = tabs.map {
            if (it.id == id) it.copy(url = url ?: it.url, title = title ?: it.title) else it
        })
    }

    fun setGhostMode(on: Boolean): TabsState = if (on == ghostMode) this else copy(ghostMode = on)

    /** Removes every tab of the current mode. Returns the new state and what was removed (for undo). */
    fun clearVisible(): Pair<TabsState, ClearedTabs> {
        val removed = visible
        val cleared = ClearedTabs(removed, selected?.id, ghostMode)
        val remaining = tabs.filter { it.ghost != ghostMode }
        return withSelected(ghostMode, null).copy(tabs = remaining) to cleared
    }

    /** Puts back what [clearVisible] removed, before any tab opened since, and reselects the old one. */
    fun restore(cleared: ClearedTabs): TabsState {
        if (cleared.tabs.isEmpty()) return this
        val known = tabs.map { it.id }.toSet()
        val back = cleared.tabs.filter { it.id !in known }
        val current = if (cleared.ghost) selectedGhost else selectedNormal
        val selectedAgain = current ?: cleared.selectedId?.takeIf { id -> back.any { it.id == id } }
        val ordered = back + tabs
        return withSelected(cleared.ghost, selectedAgain).copy(tabs = ordered)
    }

    private fun withSelected(ghost: Boolean, id: Long?): TabsState =
        if (ghost) copy(selectedGhost = id) else copy(selectedNormal = id)
}

/** The tabs a "Clear All Tabs" removed; kept for a short while so that Undo can bring them back. */
data class ClearedTabs(val tabs: List<Tab>, val selectedId: Long?, val ghost: Boolean)
