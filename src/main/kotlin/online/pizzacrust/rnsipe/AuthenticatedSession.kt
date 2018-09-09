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
        scheduledExecutor.schedule({
            renew()
        }, 5, TimeUnit.MINUTES)
    }

    fun renew() {
        val reauthed = authenticate(username, password)
        this.rKey = reauthed.rKey
        this.vKey = reauthed.vKey
    }

    fun authReq(builder: HttpRequest.Builder.() -> Unit): HttpRequest {
        val obj = HttpRequest.newBuilder()
        builder(obj)
        return obj.build()
    }

}