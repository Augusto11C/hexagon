package com.hexagonkt.handlers.async

import java.util.concurrent.CompletableFuture

/**
 * Context for an event.
 *
 * @param T Event type.
 */
interface Context<T : Any> {
    val event: T
    val predicate: (Context<T>) -> Boolean
    val nextHandlers: List<Handler<T>>
    val nextHandler: Int
    val exception: Exception?
    val attributes: Map<*, *>

    fun with(
        event: T = this.event,
        predicate: (Context<T>) -> Boolean = this.predicate,
        nextHandlers: List<Handler<T>> = this.nextHandlers,
        nextHandler: Int = this.nextHandler,
        exception: Exception? = this.exception,
        attributes: Map<*, *> = this.attributes,
    ): Context<T>

    fun next(): CompletableFuture<Context<T>> {
        for (index in nextHandler until nextHandlers.size) {
            val handler = nextHandlers[index]
            val p = handler.predicate
            if (p(this))
                return handler.process(with(predicate = p, nextHandler = index + 1))
        }

        return CompletableFuture.completedFuture(this)
    }
}
