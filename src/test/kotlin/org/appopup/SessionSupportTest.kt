package org.appopup

import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.then
import org.junit.Test

class SessionSupportTest {
    @Test
    fun `if no cookie is present, a session is created`() {
        val app = SessionSupport(Sessions()).then({ _: Request -> Response(Status.OK) })
        app(Request(Method.GET, "/")).cookies().filter { it.name == SessionSupport.cookieName }
            .shouldMatch(!isEmpty)
    }

    @Test
    fun `if cookie is present, a session is restored`() {
        val sessions = Sessions()
        val sessionKey = SessionKey("my-session")
        sessions.store(sessionKey, "my-value")
        val app = SessionSupport(sessions).then({ request: Request -> Response(Status.OK).body(request.session(sessions).retrieve<String>()!!) })
        app(Request(Method.GET, "/").cookie(SessionSupport.cookieName, "my-session")).bodyString().shouldMatch(equalTo("my-value"))
    }

    @Test
    fun `if session is destroyed, cookie is removed`() {
        val sessions = Sessions()
        val sessionKey = SessionKey("my-session")
        sessions.store(sessionKey, "my-value")
        val app = SessionSupport(sessions).then({ request: Request ->
            request.session(sessions).destroy()
            Response(Status.OK)
        })
        val response = app(Request(Method.GET, "/").cookie(SessionSupport.cookieName, "my-session"))
        response
            .cookies().filter { it.name == SessionSupport.cookieName }.first().maxAge
            .shouldMatch(equalTo(0L))
    }
}