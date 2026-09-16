package org.opensources.courses.core.common

import javax.inject.Qualifier

/** Dispatcher used for blocking I/O (network body parsing, disk). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher used for CPU-bound work kept off the main thread (search ranking, list sorting). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Process-wide scope for work that must outlive a screen (sync, catalog refresh). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
