package com.xiaojia.core

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * 灵枢入口用自签证书。配对时用户核对指纹，App 记下证书公钥的 SHA-256（SPKI），
 * 之后只信这一把公钥：换了证书、中间人、连错设备，一律拒绝。
 */
object Tls {
    fun spkiSha256(cert: X509Certificate): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)

    /** 与灵枢 `xiaojia-hub app-pin` 显示的指纹算法一致：前 12 字节，4 位一组。 */
    fun fingerprint(digest: ByteArray): String =
        digest.copyOf(12).joinToString("") { "%02X".format(it) }.chunked(4).joinToString(" ")

    class PinnedTrustManager(private val pin: ByteArray) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("服务器没有提供证书")
            leaf.checkValidity()
            if (!MessageDigest.isEqual(spkiSha256(leaf), pin)) {
                throw CertificateException("证书公钥与配对时不一致")
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("不接受客户端证书")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** 只在配对第一步使用：记下服务器公钥给用户核对。此时不发送任何凭据。 */
    class ProbeTrustManager : X509TrustManager {
        @Volatile
        var seen: ByteArray? = null
            private set

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("服务器没有提供证书")
            seen = spkiSha256(leaf)
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
            throw CertificateException("不接受客户端证书")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun socketFactory(tm: X509TrustManager): SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }.socketFactory

    /** 身份由公钥固定保证，不再按主机名校验（证书里的 IP 可能和手机填的地址写法不同）。 */
    val PinnedHostnames = HostnameVerifier { _, _ -> true }
}
