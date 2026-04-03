package com.daba.chess

import android.content.Context
import java.io.*

class StockfishEngine(private val ctx: Context) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    var isReady = false
    var skillLevel = 10  // 0-20
    var depth = 12

    fun init(): Boolean {
        return try {
            val bin = File(ctx.filesDir, "stockfish")
            if (!bin.exists() || !bin.canExecute()) {
                ctx.assets.open("stockfish").use { inp ->
                    FileOutputStream(bin).use { out -> inp.copyTo(out) }
                }
                bin.setExecutable(true)
            }
            process = ProcessBuilder(bin.absolutePath)
                .redirectErrorStream(true).start()
            writer = process!!.outputStream.bufferedWriter()
            reader  = process!!.inputStream.bufferedReader()
            send("uci"); waitFor("uciok", 3000)
            send("setoption name Skill Level value $skillLevel")
            send("isready"); waitFor("readyok", 3000)
            isReady = true; true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun setSkill(level: Int) {
        skillLevel = level.coerceIn(0, 20)
        depth = when {
            level <= 3  -> 3
            level <= 7  -> 6
            level <= 12 -> 10
            level <= 17 -> 14
            else        -> 20
        }
        send("setoption name Skill Level value $skillLevel")
    }

    fun getBestMove(fen: String, moves: List<String>, thinkMs: Int = 1500): String? {
        if (!isReady) return null
        return try {
            val mv = if (moves.isEmpty()) "" else " moves ${moves.joinToString(" ")}"
            send("position fen $fen$mv")
            send("go movetime $thinkMs depth $depth")
            var best: String? = null
            val deadline = System.currentTimeMillis() + thinkMs + 3000
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: break
                if (line.startsWith("bestmove")) {
                    val parts = line.split(" ")
                    best = if (parts.size > 1 && parts[1] != "(none)") parts[1] else null
                    break
                }
            }
            best
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private fun send(cmd: String) {
        try { writer?.write("$cmd\n"); writer?.flush() } catch (e: Exception) {}
    }

    private fun waitFor(token: String, timeoutMs: Long = 5000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        return try {
            while (System.currentTimeMillis() < deadline) {
                val line = reader?.readLine() ?: return false
                if (token in line) return true
            }
            false
        } catch (e: Exception) { false }
    }

    fun stop() { try { send("quit"); process?.destroy() } catch (e: Exception) {} }
}
