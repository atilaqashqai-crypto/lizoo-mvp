package ir.lizoo.courier.data

import android.content.Context

object Graph {
    @Volatile private var tokenStore: TokenStore? = null
    @Volatile private var repository: Repository? = null

    fun tokens(ctx: Context): TokenStore =
        tokenStore ?: synchronized(this) {
            tokenStore ?: TokenStore(ctx.applicationContext).also { tokenStore = it }
        }

    fun repo(ctx: Context): Repository =
        repository ?: synchronized(this) {
            repository ?: Repository(tokens(ctx)).also { repository = it }
        }
}
