package com.lingshu.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 连接 lingshu 的地址与配对时固定的公钥。 */
data class Endpoint(val host: String, val port: Int, val pin: ByteArray) {
    val display: String get() = "$host:$port"
    val fingerprint: String get() = Tls.fingerprint(pin)

    override fun equals(other: Any?): Boolean =
        other is Endpoint && host == other.host && port == other.port && pin.contentEquals(other.pin)

    override fun hashCode(): Int = (host.hashCode() * 31 + port) * 31 + pin.contentHashCode()
}

class HubException(val status: Int, message: String) : Exception(message) {
    val unauthorized: Boolean get() = status == 401
    val offline: Boolean get() = status == OFFLINE

    companion object {
        const val OFFLINE = -2
        const val BAD_CERT = -1
    }
}

data class Me(
    val member: String,
    val name: String,
    val role: String,
    val features: Set<String>,
    val expiresAt: String,
)

data class Brief(val greeting: String, val care: String, val weather: String?, val unread: Int, val speech: String)

data class Notice(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    val createdAt: String,
    val read: Boolean = false,
)

data class NoticePage(val items: List<Notice>, val latestId: Long)

/** 一条 R3 待确认项。args 只保留标量，界面上按 key=value 展示。 */
data class Approval(
    val id: String,
    val tool: String,
    val description: String?,
    val args: Map<String, String>,
    val createdAt: String,
    val expiresAt: String,
)

/**
 * lingshu 的家庭 App 接口。只用系统自带的 HttpsURLConnection 与 org.json，不引第三方网络库。
 * 每个请求都走固定公钥的 TLS。
 */
class HubApi(
    private val endpoint: () -> Endpoint?,
    private val token: () -> String?,
    private val onUnauthorized: () -> Unit,
) {
    @Volatile
    private var cached: Pair<Endpoint, SSLSocketFactory>? = null

    private fun factoryFor(ep: Endpoint): SSLSocketFactory {
        cached?.let { (e, f) -> if (e == ep) return f }
        return Tls.socketFactory(Tls.PinnedTrustManager(ep.pin)).also { cached = ep to it }
    }

    // ---- 配对：取服务器公钥给用户核对（不带凭据） ----

    suspend fun probe(host: String, port: Int): ByteArray = withContext(Dispatchers.IO) {
        val tm = Tls.ProbeTrustManager()
        val conn = URL("https", host, port, "/healthz").openConnection() as HttpsURLConnection
        conn.sslSocketFactory = Tls.socketFactory(tm)
        conn.hostnameVerifier = Tls.PinnedHostnames
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        try {
            val code = conn.responseCode
            if (code != 200) throw HubException(code, "地址可以连上，但对方不是 lingshu（HTTP $code）")
            tm.seen ?: throw HubException(HubException.BAD_CERT, "没有拿到证书")
        } catch (e: IOException) {
            throw HubException(HubException.OFFLINE, "连不上 $host:$port，请确认手机连着家里的 Wi-Fi、地址正确")
        } finally {
            conn.disconnect()
        }
    }

    // ---- 登录 ----

    /** 返回（会话 token，成员 ID）。 */
    suspend fun login(username: String, password: String, totp: String): Pair<String, String> {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("totp", totp)
            .put("client", "app")
        val r = request("POST", "/v1/auth/login", body, auth = false)
        return r.getString("token") to r.getString("member")
    }

    /** 作废指定会话。本地会话此时已清除，所以显式传入 token，失败也不回调 onUnauthorized。 */
    suspend fun logout(sessionToken: String) {
        request("POST", "/v1/auth/logout", auth = false, explicitToken = sessionToken)
    }

    suspend fun me(): Me {
        val r = request("GET", "/v1/app/me")
        val features = r.optJSONArray("features")?.let { arr -> (0 until arr.length()).map(arr::getString).toSet() }
        return Me(
            member = r.getString("member"),
            name = r.getString("name"),
            role = r.getString("role"),
            features = features ?: emptySet(),
            expiresAt = r.getJSONObject("session").getString("expires_at"),
        )
    }

    // ---- 小助理 ----

    suspend fun brief(): Brief {
        val r = request("GET", "/v1/app/brief")
        val w = r.optJSONObject("weather")
        val weather = w?.let {
            val t = if (it.isNull("temperature")) "" else " ${it.get("temperature")}${it.optString("unit", "°C")}"
            it.optString("condition") + t
        }
        return Brief(r.getString("greeting"), r.getString("care"), weather, r.optInt("unread"), r.getString("speech"))
    }

    suspend fun chat(conversationId: String, turns: List<Pair<String, String>>): String {
        val messages = JSONArray()
        turns.forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        val body = JSONObject().put("conversation_id", conversationId).put("messages", messages)
        return request("POST", "/v1/app/chat", body, readTimeoutMs = 75_000).getString("reply")
    }

    suspend fun notifications(after: Long, waitSeconds: Int): NoticePage {
        val r = request("GET", "/v1/app/notifications?after=$after&wait=$waitSeconds", readTimeoutMs = (waitSeconds + 15) * 1000)
        val arr = r.getJSONArray("items")
        val items = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Notice(
                o.getLong("id"),
                o.getString("kind"),
                o.getString("title"),
                o.optString("body"),
                o.getString("created_at"),
                o.optBoolean("read"),
            )
        }
        return NoticePage(items, r.getLong("latest_id"))
    }

    /** 最近的通知，新的在前。lingshu 按 ID 升序分页（每页 50），这里翻到最后一页、只留末尾 [limit] 条。 */
    suspend fun recentNotifications(limit: Int = 50): List<Notice> {
        val kept = ArrayDeque<Notice>()
        var after = 0L
        while (true) {
            val page = notifications(after, 0)
            page.items.forEach {
                kept.addLast(it)
                if (kept.size > limit) kept.removeFirst()
            }
            if (page.items.size < PAGE_SIZE || page.latestId <= after) break
            after = page.latestId
        }
        return kept.reversed()
    }

    suspend fun markRead(id: Long) {
        request("POST", "/v1/app/notifications/$id/read")
    }

    // ---- R3 待确认 ----

    suspend fun approvals(): List<Approval> {
        val arr = request("GET", "/v1/app/approvals").getJSONArray("items")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Approval(
                id = o.getString("id"),
                tool = o.getString("tool"),
                description = if (o.isNull("description")) null else o.optString("description").takeIf { it.isNotBlank() },
                args = flatArgs(o.optJSONObject("args")),
                createdAt = o.getString("created_at"),
                expiresAt = o.getString("expires_at"),
            )
        }
    }

    /** 本人在 App 里确认一条待确认项；过期、已处理、非本人都由 lingshu 返回 400。 */
    suspend fun confirmApproval(id: String) {
        request("POST", "/v1/app/approvals/$id/confirm")
    }

    private fun flatArgs(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = linkedMapOf<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!o.isNull(k)) out[k] = o.get(k).toString()
        }
        return out
    }

    // ---- 底层 ----

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
        readTimeoutMs: Int = 15_000,
        explicitToken: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val ep = endpoint() ?: throw HubException(0, "还没有配对 lingshu")
        val conn = URL("https", ep.host, ep.port, path).openConnection() as HttpsURLConnection
        conn.sslSocketFactory = factoryFor(ep)
        conn.hostnameVerifier = Tls.PinnedHostnames
        conn.connectTimeout = 5000
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = method
        conn.useCaches = false
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        val t = explicitToken ?: if (auth) token() ?: throw HubException(401, "请先登录") else null
        if (t != null) conn.setRequestProperty("Authorization", "Bearer $t")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.let(::readLimited).orEmpty()
            if (code !in 200..299) {
                if (code == 401 && auth) onUnauthorized()
                throw HubException(code, detailOf(text) ?: "lingshu 返回 $code")
            }
            JSONObject(text.ifEmpty { "{}" })
        } catch (e: SSLException) {
            throw HubException(HubException.BAD_CERT, "证书与配对时不一致，可能连到了别的设备。请在设置里重新配对")
        } catch (e: IOException) {
            throw HubException(HubException.OFFLINE, "连不上 lingshu，请确认手机连着家里的 Wi-Fi")
        } finally {
            conn.disconnect()
        }
    }

    /** 响应最多读 1 MB：对方异常时不把内存撑爆。 */
    private fun readLimited(input: InputStream): String = input.use { stream ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            if (out.size() + n > MAX_RESPONSE_BYTES) throw HubException(0, "lingshu 的响应过大")
            out.write(buf, 0, n)
        }
        out.toString(Charsets.UTF_8.name())
    }

    private fun detailOf(text: String): String? = try {
        when (val d = JSONObject(text).opt("detail")) {
            is String -> d
            is JSONArray -> "提交的内容不符合要求"
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val MAX_RESPONSE_BYTES = 1 shl 20
    }
}
