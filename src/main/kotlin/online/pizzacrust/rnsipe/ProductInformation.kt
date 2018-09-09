package online.pizzacrust.rnsipe

import com.google.gson.Gson
import jdk.incubator.http.HttpClient
import jdk.incubator.http.HttpRequest
import jdk.incubator.http.HttpResponse
import java.net.URI

data class ProductInformation(val id: Long, val price: Int, val sellerId: Long = 1)

/**
 * If BestPrice is null, then that means that there are still copies of it at MSRP price.
 * If Price is null, then that means that there are no longer any copies at MSRP but has resellers.
 */
data class ItemResponse(var AbsoluteUrl: String, var Price: Int?, var BestPrice: Int?, var
AssetId: Long) {
    constructor() : this("", 0, 0, 0)
}

private fun convertResponseToInfo(itemResponse: ItemResponse): ProductInformation? {
    if (itemResponse.BestPrice != null) return null
    return ProductInformation(itemResponse.AssetId, itemResponse.Price!!)
}

data class ItemsResponse(var Items: List<ItemResponse>) {
    constructor() : this(mutableListOf())
}

val productInfoClient = HttpClient.newHttpClient()

/**
 * This will retrieve only valid MSRP purchasable limiteds that are by ROBLOX.
 * TODO Add reseller support
 */
fun findRecentlyUpdatedLimiteds(): MutableList<ProductInformation> {
    val list = mutableListOf<ProductInformation>()
    val url = "https://search.roblox.com/catalog/items?Category=2&Direction=2&Subcategory=2&SortType=3"
    val request = HttpRequest.newBuilder(URI(url)).GET().build()
    val responseString = productInfoClient.send(request, HttpResponse.BodyHandler.asString()).body()
    val response = Gson().fromJson<ItemsResponse>(responseString, ItemsResponse::class.java)
    for (item in response.Items) {
        val convert = convertResponseToInfo(item)
        if (convert != null) list.add(convert)
    }
    return list
}