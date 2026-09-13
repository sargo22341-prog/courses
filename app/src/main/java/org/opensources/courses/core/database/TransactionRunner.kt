package org.opensources.courses.core.database

import androidx.room.withTransaction
import javax.inject.Inject

/** Runs a block atomically. Abstracted so repositories do not depend on [CoursesDatabase] itself. */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner
    @Inject
    constructor(
        private val database: CoursesDatabase,
    ) : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = database.withTransaction(block)
    }
