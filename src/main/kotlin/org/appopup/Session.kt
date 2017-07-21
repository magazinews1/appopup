package org.appopup

import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
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

    inline fun <reified T> retrieve(sessionKey: SessionKey): T? = storage[sessionKey].takeIf { it is T } as T?
}

data class SessionKey(val value: String)

object SessionGenerator {
    private val random = SecureRandom()
    fun create(): SessionKey = SessionKey(BigInteger(128, random).toString(32))
}

object Session {
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
                next(request)
            }

        }
    }
}

fun Request.storeInSession(sessionStorage: Sessions, value: Any) = sessionStorage.store(sessionKey(), value)
inline fun <reified T> Request.retrieveKeyFromSession(sessionStorage: Sessions): T? = sessionStorage.retrieve(sessionKey())
fun Request.destroySession(sessionStorage: Sessions) = sessionStorage.destroy(sessionKey())
fun Request.sessionKey(): SessionKey = cookie(Session.cookieName)?.let { SessionKey(it.value) } ?: throw IllegalStateException("Session is not present")