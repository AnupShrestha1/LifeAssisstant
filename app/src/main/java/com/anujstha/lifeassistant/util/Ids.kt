package com.anujstha.lifeassistant.util

import java.util.UUID

/** Creates primary keys. UUIDs keep ids unique across devices, backups and any future sync. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}

/** Escapes `\`, `%` and `_` so user text is matched literally by `LIKE ... ESCAPE '\'`. */
fun escapeLike(text: String): String = buildString(text.length) {
    for (c in text) {
        if (c == '\\' || c == '%' || c == '_') append('\\')
        append(c)
    }
}
