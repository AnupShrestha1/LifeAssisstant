package com.anujstha.lifeassistant.domain.model

/** Invalid user input rejected by a repository. [message] is safe to show to the user. */
class ValidationException(message: String) : IllegalArgumentException(message)

internal fun validate(condition: Boolean, message: () -> String) {
    if (!condition) throw ValidationException(message())
}
