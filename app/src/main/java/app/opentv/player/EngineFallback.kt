package app.opentv.player

/** A fresh policy per URL/retry. Never loops between engines on a broken source. */
internal class EngineFallback {
    enum class Engine { EXO, VLC }
    var engine = Engine.EXO
        private set
    fun next(): Boolean {
        if (engine == Engine.VLC) return false
        engine = Engine.VLC
        return true
    }
}
