package com.hexagonkt.http.test.async.examples

import com.hexagonkt.core.fail
import com.hexagonkt.handlers.async.done
import com.hexagonkt.http.client.HttpClientPort
import com.hexagonkt.http.model.NOT_FOUND_404
import com.hexagonkt.http.model.HttpStatus
import com.hexagonkt.http.model.Header
import com.hexagonkt.http.model.INTERNAL_SERVER_ERROR_500
import com.hexagonkt.http.server.async.HttpServerPort
import com.hexagonkt.http.server.async.HttpServerSettings
import com.hexagonkt.http.handlers.async.PathHandler
import com.hexagonkt.http.handlers.async.HttpHandler
import com.hexagonkt.http.handlers.async.path
import com.hexagonkt.http.test.async.BaseTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

@Suppress("FunctionName") // This class's functions are intended to be used only in tests
abstract class ErrorsTest(
    final override val clientAdapter: () -> HttpClientPort,
    final override val serverAdapter: () -> HttpServerPort,
    final override val serverSettings: HttpServerSettings = HttpServerSettings(),
) : BaseTest() {

    // errors
    class CustomException : IllegalArgumentException()

    private val path: PathHandler = path {

        /*
         * Catching `Exception` handles any unhandled exception, has to be the last executed (first
         * declared)
         */
        exception<Exception>(NOT_FOUND_404) {
            internalServerError("Root handler").done()
        }

        exception<IllegalArgumentException> {
            val error = exception?.message ?: exception?.javaClass?.name ?: fail
            val newHeaders = response.headers + Header("runtime-error", error)
            send(HttpStatus(598), "Runtime", headers = newHeaders).done()
        }

        exception<UnsupportedOperationException> {
            val error = exception?.message ?: exception?.javaClass?.name ?: fail
            val newHeaders = response.headers + Header("error", error)
            send(HttpStatus(599), "Unsupported", headers = newHeaders).done()
        }

        get("/exception") { throw UnsupportedOperationException("error message") }
        get("/baseException") { throw CustomException() }
        get("/unhandledException") { error("error message") }
        get("/invalidBody") { ok(LocalDateTime.now()).done() }

        get("/halt") { internalServerError("halted").done() }
        get("/588") { send(HttpStatus(588)).done() }

        // It is possible to execute a handler upon a given status code before returning
        on(pattern = "*", status = HttpStatus(588)) {
            send(HttpStatus(578), "588 -> 578").done()
        }
    }
    // errors

    override val handler: HttpHandler = path

    @Test fun `Invalid body returns 500 status code`() {
        val response = client.get("/invalidBody")
        val message = "Unsupported body type: LocalDateTime"
        assertResponseContains(response, INTERNAL_SERVER_ERROR_500, message)
    }

    @Test fun `Halt stops request with 500 status code`() {
        val response = client.get("/halt")
        assertResponseEquals(response, INTERNAL_SERVER_ERROR_500, "halted")
    }

    @Test fun `Handling status code allows to change the returned code`() {
        val response = client.get("/588")
        assertResponseEquals(response, HttpStatus(578), "588 -> 578")
    }

    @Test fun `Handle exception allows to catch unhandled callback exceptions`() {
        val response = client.get("/exception")
        assertEquals("error message", response.headers["error"]?.value)
        assertResponseContains(response, HttpStatus(599), "Unsupported")
    }

    @Test fun `Base error handler catch all exceptions that subclass a given one`() {
        val response = client.get("/baseException")
        val runtimeError = response.headers["runtime-error"]?.value
        assertEquals(CustomException::class.java.name, runtimeError)
        assertResponseContains(response, HttpStatus(598), "Runtime")
    }

    @Test fun `A runtime exception returns a 500 code`() {
        val response = client.get("/unhandledException")
        assertResponseContains(response, INTERNAL_SERVER_ERROR_500, "Root handler")
    }
}
