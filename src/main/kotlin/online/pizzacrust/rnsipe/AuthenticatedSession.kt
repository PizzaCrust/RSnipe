package online.pizzacrust.rnsipe

import com.google.gson.Gson
import jdk.incubator.http.HttpClient
import jdk.incubator.http.HttpRequest
import jdk.incubator.http.HttpResponse
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val authClient = HttpClient.newHttpClient()

private fun verifyToken(): String {
    val request = HttpRequest.newBuilder(URI("https://www.roblox.com/")).GET().build()
    val response = authClient.send(request, HttpResponse.BodyHandler.asString()).body()
    val part = response.substring(response.indexOf("XsrfToken.setToken('") + 1)
    return part.substring(0, part.indexOf('\''))
}

private data class LoginRequest(var ctype: String, var cvalue: String, var password: String) {
    constructor() : this("", "", "")
}

fun authenticate(username: String, password: String): AuthenticatedSession {
    val url = "https://auth.roblox.com/v2/login"
    val reqBody = Gson().toJson(LoginRequest("Username", username, password))
    val vKey = verifyToken()
    val request = HttpRequest.newBuilder(URI(url)).header("X-CSRF-TOKEN", vKey).POST(HttpRequest
            .BodyPublisher.fromString(reqBody)).build()
    val response = authClient.send(request, HttpResponse.BodyHandler.asString())
    val authToken = response.headers().firstValue("Set-Cookie").get().split(";")[0]
    return AuthenticatedSession("$authToken;", vKey, username, password)
}

class AuthenticatedSession internal constructor(var rKey: String,
                                                var vKey: String,
                                                val username: String,
                                                val password: String) {

    init {
        val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutor.scheduleAtFixedRate({
            renew()
        }, 0, 5, TimeUnit.MINUTES)
    }

    fun renew() {
        val reauthed = authenticate(username, password)
        this.rKey = reauthed.rKey
        this.vKey = reauthed.vKey
    }

    fun authReq(builder: HttpRequest.Builder.() -> Unit): HttpRequest {
        val obj = HttpRequest.newBuilder()
        obj.setHeader("Cookie", rKey)
        obj.setHeader("X-CSRF-TOKEN", vKey)
        builder(obj)
        return obj.build()
    }

    fun purchase(productInfo: ProductInformation) = authClient.send(authReq {
        val url = "https://www.roblox.com/api/item" +
                ".ashx?rqtype=purchase&productID=${productInfo
                        .id}&expectedCurrency=1&expectedPrice" +
                "=${productInfo.price}" +
                "&expectedSellerID=${productInfo.sellerId}&userAssetID="
        uri(URI(url))
        POST(HttpRequest.BodyPublisher.noBody())
    }, HttpResponse.BodyHandler.asString())

}

private val snipedItems: MutableList<Long> = mutableListOf()

private data class BalanceCountResponse(var robux: Int) {
    constructor():this(0)
}

private fun robuxCount(authSession: AuthenticatedSession): Int {
    val url = "http://api.roblox.com/currency/balance"
    val responseString = authClient.send(authSession.authReq {
        uri(URI(url))
        GET()
    }, HttpResponse.BodyHandler.asString()).body()
    val response = Gson().fromJson<BalanceCountResponse>(responseString, BalanceCountResponse::class.java)
    return response.robux
}

fun startSniping(authSession: AuthenticatedSession) {
    val executor = Executors.newSingleThreadScheduledExecutor()
    executor.scheduleAtFixedRate({
        for (limited in findRecentlyUpdatedLimiteds()) {
            if (!snipedItems.contains(limited.id) && robuxCount(authSession) >= limited.price) {
                println("sniping ${limited.id}")
                authSession.purchase(limited)
                snipedItems.add(limited.id)
            }
        }
    }, 0, 20, TimeUnit.SECONDS)
}

fun main(vararg strings: String) {
    if (strings.size < 2) {
        println("not enough arguments, must have 2 (username and password)")
        return
    }
    println("starting sniper...")
    startSniping(authenticate(strings[0], strings[1]))
}