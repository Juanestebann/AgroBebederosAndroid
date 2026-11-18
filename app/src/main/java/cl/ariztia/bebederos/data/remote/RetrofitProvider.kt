package cl.ariztia.bebederos.data.remote

import cl.ariztia.bebederos.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Configuración compartida del cliente del backend propio de AgroBebederos. */
object RetrofitProvider {
    val credentials = InMemoryCredentials()

    val okHttpClient: OkHttpClient by lazy {
        createOkHttpClient(credentials)
    }

    val retrofit: Retrofit by lazy {
        createRetrofit(
            baseUrl = BuildConfig.BACKEND_BASE_URL,
            client = okHttpClient
        )
    }

    fun createOkHttpClient(credentials: InMemoryCredentials): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(credentials))
            .build()

    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    fun <T> create(service: Class<T>): T = retrofit.create(service)
}
