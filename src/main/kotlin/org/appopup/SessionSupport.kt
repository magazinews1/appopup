package org.appopup

import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.invalidateCookie
import java.math.BigInteger
import java.security.SecureRandom


class Sessions {
    val storage = mutableMapOf<SessionKey, Any?>()

    fun valid(key: SessionKey): Boolean = storage.containsKey(key)

    fun create(): SessionKey {
        val newKey = SessionGenerator.create()
        storage.put(newKey, null)
        return newKey
    }

    fun store(sessionKey: SessionKey, value: Any): Unit {
        storage.put(sessionKey, value)
    }

    fun destroy(sessionKey: SessionKey): Unit {
        storage.remove(sessionKey)
    }

    fun present(sessionKey: SessionKey): Boolean = storage.containsKey(sessionKey)

    inline fun <reified T> retrieve(sessionKey: SessionKey): T? = storage[sessionKey].takeIf { it is T } as T?
}

data class SessionKey(val value: String)

object SessionGenerator {
    private val random = SecureRandom()
    fun create(): SessionKey = SessionKey(BigInteger(128, random).toString(32))
}

object SessionSupport {
    val cookieName = "h4k"

    operator fun invoke(sessions: Sessions): Filter = Filter {
        next ->
        { request: Request ->
            val key = request.cookie(cookieName)?.let { SessionKey(it.value) }?.takeIf { sessions.valid(it) }
            if (key == null) {
                val newKey = sessions.create()
                val response = next(request.cookie(cookieName, newKey.value))
                response.cookie(Cookie(cookieName, newKey.value))
            } else {
                val response = next(request)
                if (!sessions.present(key)) {
                    response.invalidateCookie("h4k")
                } else {
                    response
                }
            }

        }
    }
}

class Session(val sessions: Sessions, val sessionKey: SessionKey) {
    fun store(value: Any) = sessions.store(sessionKey, value)
    inline fun <reified T> retrieve(): T? = sessions.retrieve(sessionKey)
    fun destroy() = sessions.destroy(sessionKey)
}

fun Request.session(sessionStorage: Sessions): Session {
    val sessionKey = cookie(SessionSupport.cookieName)?.let { SessionKey(it.value) }
        ?: throw IllegalStateException("Session is not present")
    return Session(sessionStorage, sessionKey)
}
