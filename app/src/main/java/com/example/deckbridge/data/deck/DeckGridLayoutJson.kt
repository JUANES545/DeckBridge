package com.example.deckbridge.data.deck

import android.content.res.Resources
import com.example.deckbridge.domain.deck.DeckGridActionKind
import com.example.deckbridge.domain.deck.DeckGridButtonPersisted
import com.example.deckbridge.domain.deck.DeckGridLayoutPersisted
import com.example.deckbridge.domain.deck.DeckKnobActionPersisted
import com.example.deckbridge.domain.deck.DeckKnobPersisted
import com.example.deckbridge.domain.deck.DeckKnobsLayoutPersisted
import com.example.deckbridge.domain.deck.DeckMultiPageSurface
import com.example.deckbridge.domain.deck.DeckPagesPersisted
import com.example.deckbridge.domain.deck.DeckPersistedSurface
import org.json.JSONArray
import org.json.JSONObject

private const val KEY_SCHEMA = "schemaVersion"
private const val KEY_GRID = "grid"
private const val KEY_KNOBS = "knobs"
private const val KEY_PAGES = "pages"
private const val KEY_ACTIVE_PAGE = "activePageIndex"
private const val KEY_BUTTONS = "buttons"
private const val KEY_ROTATE_CCW = "rotateCcw"
private const val KEY_ROTATE_CW = "rotateCw"
private const val KEY_PRESS = "press"
private const val KEY_ID = "id"
private const val KEY_SORT = "sortIndex"
private const val KEY_LABEL = "label"
private const val KEY_SUBTITLE = "subtitle"
private const val KEY_KIND = "kind"
private const val KEY_INTENT_ID = "intentId"
private const val KEY_PAYLOAD = "payload"
private const val KEY_ICON = "iconToken"
private const val KEY_ENABLED = "enabled"
private const val KEY_VISIBLE = "visible"
private const val KEY_PAGE_NAME = "name"

object DeckGridLayoutJson {

    fun encode(surface: DeckPersistedSurface): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, surface.schemaVersion)
        root.put(KEY_GRID, gridToJson(surface.grid))
        root.put(KEY_KNOBS, knobsToJson(surface.knobs))
        return root.toString()
    }

    /**
     * [res] is used when `knobs` is absent or invalid (legacy JSON) to seed labels from resources.
     */
    fun decode(json: String, res: Resources): DeckPersistedSurface? = runCatching {
        val root = JSONObject(json)
        val schema = root.optInt(KEY_SCHEMA, 1)
        val gridObj = root.getJSONObject(KEY_GRID)
        val grid = gridFromJson(gridObj) ?: return@runCatching null
        val defaultKnobs = DeckKnobPreset.defaultKnobsFromResources(res)
        val knobsJson = root.optJSONArray(KEY_KNOBS)
        val knobs = knobsFromJson(knobsJson) ?: defaultKnobs
        val schemaOut = maxOf(schema, DeckPersistedSurface.CURRENT_SCHEMA_VERSION)
        DeckPersistedSurface(schemaVersion = schemaOut, grid = grid, knobs = knobs)
    }.getOrNull()

    fun surfaceMissingKnobsSection(json: String): Boolean = runCatching {
        !JSONObject(json).has(KEY_KNOBS)
    }.getOrDefault(true)

    private fun gridToJson(grid: DeckGridLayoutPersisted): JSONObject {
        val arr = JSONArray()
        grid.sortedButtons().forEach { b ->
            arr.put(buttonToJson(b))
        }
        val obj = JSONObject().put(KEY_BUTTONS, arr)
        if (!grid.name.isNullOrBlank()) obj.put(KEY_PAGE_NAME, grid.name)
        return obj
    }

    private fun knobsToJson(layout: DeckKnobsLayoutPersisted): JSONArray {
        val arr = JSONArray()
        layout.sortedKnobs().forEach { k ->
            arr.put(knobToJson(k))
        }
        return arr
    }

    private fun knobToJson(k: DeckKnobPersisted): JSONObject =
        JSONObject().apply {
            put(KEY_ID, k.id)
            put(KEY_SORT, k.sortIndex)
            put(KEY_LABEL, k.label)
            put(KEY_SUBTITLE, k.subtitle)
            put(KEY_ROTATE_CCW, knobActionToJson(k.rotateCcw))
            put(KEY_ROTATE_CW, knobActionToJson(k.rotateCw))
            put(KEY_PRESS, knobActionToJson(k.press))
            put(KEY_ICON, k.iconToken)
            put(KEY_ENABLED, k.enabled)
            put(KEY_VISIBLE, k.visible)
        }

    private fun knobActionToJson(a: DeckKnobActionPersisted): JSONObject =
        JSONObject().apply {
            put(KEY_KIND, a.kind.name.lowercase())
            put(KEY_INTENT_ID, a.intentId)
            val p = JSONObject()
            a.payload.forEach { (key, v) -> p.put(key, v) }
            put(KEY_PAYLOAD, p)
        }

    private fun knobsFromJson(arr: JSONArray?): DeckKnobsLayoutPersisted? {
        if (arr == null || arr.length() != DeckKnobsLayoutPersisted.KNOB_COUNT) return null
        val raw = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: return null
                add(knobFromJson(o) ?: return null)
            }
        }
        val normalized = raw.sortedBy { it.sortIndex }.mapIndexed { idx, k -> k.copy(sortIndex = idx) }
        return runCatching { DeckKnobsLayoutPersisted(normalized) }.getOrNull()
    }

    private fun knobFromJson(o: JSONObject): DeckKnobPersisted? {
        val id = o.optString(KEY_ID).ifBlank { return null }
        val sortIndex = o.optInt(KEY_SORT, 0)
        val label = o.optString(KEY_LABEL)
        val subtitle = o.optString(KEY_SUBTITLE)
        val rotateCcw = knobActionFromJson(o.optJSONObject(KEY_ROTATE_CCW)) ?: return null
        val rotateCw = knobActionFromJson(o.optJSONObject(KEY_ROTATE_CW)) ?: return null
        val press = knobActionFromJson(o.optJSONObject(KEY_PRESS)) ?: return null
        val icon = o.optString(KEY_ICON).ifBlank { null }
        val enabled = o.optBoolean(KEY_ENABLED, true)
        val visible = o.optBoolean(KEY_VISIBLE, true)
        return DeckKnobPersisted(
            id = id,
            sortIndex = sortIndex,
            label = label,
            subtitle = subtitle,
            rotateCcw = rotateCcw,
            rotateCw = rotateCw,
            press = press,
            enabled = enabled,
            visible = visible,
            iconToken = icon,
        )
    }

    private fun knobActionFromJson(o: JSONObject?): DeckKnobActionPersisted? {
        if (o == null) return null
        val kind = parseKind(o.optString(KEY_KIND))
        val intentId = o.optString(KEY_INTENT_ID).ifBlank { return null }
        val payload = mutableMapOf<String, String>()
        val pObj = o.optJSONObject(KEY_PAYLOAD)
        if (pObj != null) {
            val keys = pObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                payload[k] = pObj.optString(k)
            }
        }
        return DeckKnobActionPersisted(kind = kind, intentId = intentId, payload = payload)
    }

    private fun buttonToJson(b: DeckGridButtonPersisted): JSONObject =
        JSONObject().apply {
            put(KEY_ID, b.id)
            put(KEY_SORT, b.sortIndex)
            put(KEY_LABEL, b.label)
            put(KEY_SUBTITLE, b.subtitle)
            put(KEY_KIND, b.kind.name.lowercase())
            put(KEY_INTENT_ID, b.intentId)
            val p = JSONObject()
            b.payload.forEach { (k, v) -> p.put(k, v) }
            put(KEY_PAYLOAD, p)
            put(KEY_ICON, b.iconToken)
            put(KEY_ENABLED, b.enabled)
            put(KEY_VISIBLE, b.visible)
        }

    private fun gridFromJson(obj: JSONObject): DeckGridLayoutPersisted? {
        val arr = obj.optJSONArray(KEY_BUTTONS) ?: return null
        if (arr.length() != DeckGridLayoutPersisted.GRID_SLOT_COUNT) return null
        val raw = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: return null
                add(buttonFromJson(o) ?: return null)
            }
        }
        val normalized = raw.sortedBy { it.sortIndex }.mapIndexed { idx, b -> b.copy(sortIndex = idx) }
        val name = obj.optString(KEY_PAGE_NAME).ifBlank { null }
        return runCatching { DeckGridLayoutPersisted(normalized, name = name) }.getOrNull()
    }

    private fun buttonFromJson(o: JSONObject): DeckGridButtonPersisted? {
        val id = o.optString(KEY_ID).ifBlank { return null }
        val sortIndex = o.optInt(KEY_SORT, 0)
        val label = o.optString(KEY_LABEL)
        val subtitle = o.optString(KEY_SUBTITLE)
        val kind = parseKind(o.optString(KEY_KIND))
        val intentId = o.optString(KEY_INTENT_ID).ifBlank { return null }
        val payload = mutableMapOf<String, String>()
        val pObj = o.optJSONObject(KEY_PAYLOAD)
        if (pObj != null) {
            val keys = pObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                payload[k] = pObj.optString(k)
            }
        }
        val icon = o.optString(KEY_ICON).ifBlank { null }
        val enabled = o.optBoolean(KEY_ENABLED, true)
        val visible = o.optBoolean(KEY_VISIBLE, true)
        return DeckGridButtonPersisted(
            id = id,
            sortIndex = sortIndex,
            label = label,
            subtitle = subtitle,
            kind = kind,
            intentId = intentId,
            payload = payload,
            iconToken = icon,
            enabled = enabled,
            visible = visible,
        )
    }

    private fun parseKind(raw: String): DeckGridActionKind {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return DeckGridActionKind.NOOP
        val n = trimmed.uppercase().replace('-', '_')
        return DeckGridActionKind.entries.firstOrNull { it.name == n }
            ?: DeckGridActionKind.entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: DeckGridActionKind.NOOP
    }

    // ── Multi-page encode / decode ────────────────────────────────────────────

    fun encodeMultiPage(surface: DeckMultiPageSurface): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, surface.schemaVersion)
        root.put(KEY_ACTIVE_PAGE, surface.pages.activePageIndex)
        val pagesArr = JSONArray()
        surface.pages.pages.forEach { grid -> pagesArr.put(gridToJson(grid)) }
        root.put(KEY_PAGES, pagesArr)
        root.put(KEY_KNOBS, knobsToJson(surface.knobs))
        return root.toString()
    }

    /**
     * Decodes the multi-page format (schemaVersion 3).
     * If the JSON is the legacy single-grid format (has "grid" key, no "pages" key), migrates it
     * automatically by wrapping the existing grid as page 0.
     */
    fun decodeMultiPage(json: String, res: Resources): DeckMultiPageSurface? = runCatching {
        val root = JSONObject(json)
        val defaultKnobs = DeckKnobPreset.defaultKnobsFromResources(res)
        val knobsJson = root.optJSONArray(KEY_KNOBS)
        val knobs = knobsFromJson(knobsJson) ?: defaultKnobs

        // Legacy single-grid migration: wrap existing grid as page 0.
        if (!root.has(KEY_PAGES) && root.has(KEY_GRID)) {
            val gridObj = root.getJSONObject(KEY_GRID)
            val grid = gridFromJson(gridObj) ?: return@runCatching null
            val pages = DeckPagesPersisted(pages = listOf(grid), activePageIndex = 0)
            return@runCatching DeckMultiPageSurface(
                schemaVersion = DeckMultiPageSurface.CURRENT_SCHEMA_VERSION,
                pages = pages,
                knobs = knobs,
            )
        }

        val pagesArr = root.optJSONArray(KEY_PAGES) ?: return@runCatching null
        if (pagesArr.length() == 0) return@runCatching null
        val grids = buildList {
            for (i in 0 until pagesArr.length()) {
                val obj = pagesArr.optJSONObject(i) ?: return@runCatching null
                add(gridFromJson(obj) ?: return@runCatching null)
            }
        }
        val activePageIndex = root.optInt(KEY_ACTIVE_PAGE, 0)
            .coerceIn(0, grids.size - 1)
        val pages = DeckPagesPersisted(pages = grids, activePageIndex = activePageIndex)
        DeckMultiPageSurface(
            schemaVersion = DeckMultiPageSurface.CURRENT_SCHEMA_VERSION,
            pages = pages,
            knobs = knobs,
        )
    }.getOrNull()
}
