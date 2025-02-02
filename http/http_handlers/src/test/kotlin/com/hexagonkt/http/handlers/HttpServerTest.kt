package com.hexagonkt.http.handlers

import com.hexagonkt.core.require
import com.hexagonkt.http.model.METHOD_NOT_ALLOWED_405
import com.hexagonkt.http.model.NOT_FOUND_404
import com.hexagonkt.http.model.HttpMethod.GET
import com.hexagonkt.http.model.HttpMethod.PUT
import com.hexagonkt.http.model.*
import com.hexagonkt.http.model.HttpRequest
import com.hexagonkt.http.model.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals

internal class HttpServerTest {

    @Test fun `Handlers proof of concept`() {
        val path = PathHandler(
            OnHandler { ok() },
            AfterHandler { this },
            FilterHandler { next() },

            PathHandler("/a",
                OnHandler { notFound() },
                OnHandler("/{p}") {
                    send(HttpResponse(
                        status = OK_200,
                        body = pathParameters.require("p")
                    ))
                },

                PathHandler("/b",
                    OnHandler { send(status = METHOD_NOT_ALLOWED_405) },
                    OnHandler(GET) { send(status = NO_CONTENT_204) },
                    OnHandler("/{p}") {
                        send(HttpResponse(
                            status = OK_200,
                            body = pathParameters.require("p")
                        ))
                    }
                )
            )
        )

        assertEquals(NOT_FOUND_404, path.process(HttpRequest(path = "/a")).status)
        assertEquals(NO_CONTENT_204, path.process(HttpRequest(path = "/a/b")).status)
        assertEquals(METHOD_NOT_ALLOWED_405, path.process(HttpRequest(PUT, path = "/a/b")).status)
        assertEquals(OK_200, path.process(HttpRequest(path = "/a/x")).status)
        assertEquals("x", path.process(HttpRequest(path = "/a/x")).response.body)
        assertEquals(OK_200, path.process(HttpRequest(path = "/a/b/value")).status)
        assertEquals("value", path.process(HttpRequest(path = "/a/b/value")).response.body)
    }

    @Test fun `Builder proof of concept`() {

        val path = path {
            path("/a") {
                on { ok() }
                path("/b") {
                    on { send(status = ACCEPTED_202) }
                }
            }
        }

        assertEquals(OK_200, path.process(HttpRequest(path = "/a")).status)
        assertEquals(ACCEPTED_202, path.process(HttpRequest(path = "/a/b")).status)

        val contextPath = path("/p") {
            path("/a") {
                on { ok() }
                path("/b") {
                    on { send(status = ACCEPTED_202) }
                }
            }
        }

        assertEquals(NOT_FOUND_404, contextPath.process(HttpRequest(path = "/a")).status)
        assertEquals(OK_200, contextPath.process(HttpRequest(path = "/p/a")).status)
        assertEquals(ACCEPTED_202, contextPath.process(HttpRequest(path = "/p/a/b")).status)
    }
}
