package com.durgamma.festival.core.util

import java.util.UUID

/** Every record carries a UUID v4 id so offline-created rows never collide on sync (plan §20). */
fun newRecordId(): String = UUID.randomUUID().toString()
