package com.daba.chess

import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

private const val MATCH = -1
private const val WRAP  = -2

class MainActivity : AppCompatActivity() {
    private lateinit var boardView: BoardView
    private lateinit var engine: StockfishEngine
    private lateinit var statusTv: TextView
    private lateinit var homeScr: ScrollView
    private lateinit var gameScr: LinearLayout
    private lateinit var winScr: FrameLayout
    private lateinit var winTv: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private var mpMove: MediaPlayer? = null
    private var mpCapture: MediaPlayer? = null
    private var mpCheck: MediaPlayer? = null
    private var soundOn = true
    private var difficulty = 10
    private var playerIsWhite = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()
        engine = StockfishEngine(this)
        loadSounds()
        val root = FrameLayout(this)
        homeScr = buildHome(); root.addView(homeScr)
        gameScr = buildGame(); gameScr.visibility = View.GONE; root.addView(gameScr)
        winScr = buildWin(); winScr.visibility = View.GONE; root.addView(winScr)
        setContentView(root)
    }

    private fun loadSounds() {
        try {
            mpMove = MediaPlayer().apply {
                val afd = assets.openFd("move.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); prepare()
            }
        } catch (e: Exception) {}
        try {
            mpCapture = MediaPlayer().apply {
                val afd = assets.openFd("capture.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); prepare()
            }
        } catch (e: Exception) {}
        try {
            mpCheck = MediaPlayer().apply {
                val afd = assets.openFd("check.mp3")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); prepare()
            }
        } catch (e: Exception) {}
    }

    private fun playSound(capture: Boolean, check: Boolean) {
        if (!soundOn) return
        handler.post {
            try {
                when {
                    check   -> mpCheck?.let   { it.seekTo(0); it.start() }
                    capture -> mpCapture?.let { it.seekTo(0); it.start() } ?: mpMove?.let { it.seekTo(0); it.start() }
                    else    -> mpMove?.let    { it.seekTo(0); it.start() }
                }
            } catch (e: Exception) {}
        }
    }

    // =========================================================
    // HOME SCREEN
    // =========================================================
    private fun buildHome(): ScrollView {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(40, 50, 40, 50)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 0f
                colors = intArrayOf(Color.parseColor("#0F0C29"), Color.parseColor("#302B63"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }
        }

        // Logo
        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                val s = assets.open("logo.png")
                setImageBitmap(BitmapFactory.decodeStream(s))
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#302B63"))
            }
        }
        ll.addView(logo, LinearLayout.LayoutParams(260, 260).apply { setMargins(0, 0, 0, 16); gravity = Gravity.CENTER })

        ll.addView(mkTv("♟ দাবা ♟", 34f, "#F9D423", true).apply {
            setShadowLayer(14f, 0f, 3f, Color.parseColor("#FF6B35"))
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, 0, 4); gravity = Gravity.CENTER })

        ll.addView(mkTv("Stockfish Engine সহ", 14f, "#A78BFA", false),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, 0, 30); gravity = Gravity.CENTER })

        // Color choice
        ll.addView(mkTv("আপনার রঙ বেছে নিন:", 13f, "#CCCCCC", false),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, 0, 10); gravity = Gravity.CENTER })

        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }

        // Fix: declare vars first, assign after
        var whiteBtn: TextView? = null
        var blackBtn: TextView? = null
        whiteBtn = mkBtn("♔  সাদা", "#F0D9B5", "#B58863") {
            playerIsWhite = true
            whiteBtn?.let { w -> blackBtn?.let { b -> highlightColorBtns(w, b, true) } }
        }
        blackBtn = mkBtn("♚  কালো", "#302B63", "#1A1A2E") {
            playerIsWhite = false
            whiteBtn?.let { w -> blackBtn?.let { b -> highlightColorBtns(w, b, false) } }
        }
        colorRow.addView(whiteBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, 8, 0) })
        colorRow.addView(blackBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        ll.addView(colorRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, 20) })

        // Difficulty
        ll.addView(mkTv("কঠিনতা বেছে নিন:", 13f, "#CCCCCC", false),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, 0, 10); gravity = Gravity.CENTER })

        val diffRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val levels = listOf(Triple("সহজ", "#4CAF50", 3), Triple("মাঝারি", "#FF9800", 10), Triple("কঠিন", "#F44336", 18))
        for ((name, col, lvl) in levels) {
            val b = mkBtn(name, col, col) { difficulty = lvl }
            diffRow.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(4, 0, 4, 0) })
        }
        ll.addView(diffRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, 0, 0, 30) })

        ll.addView(
            mkBtn("▶  খেলা শুরু করুন", "#F9D423", "#FF6B35") { startGame() }.apply {
                textSize = 20f; setPadding(60, 22, 60, 22)
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        scroll.addView(ll); return scroll
    }

    private fun highlightColorBtns(white: TextView, black: TextView, whiteSelected: Boolean) {
        white.alpha = if (whiteSelected) 1f else 0.55f
        black.alpha = if (whiteSelected) 0.55f else 1f
    }

    // =========================================================
    // GAME SCREEN
    // =========================================================
    private fun buildGame(): LinearLayout {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0C29"))
        }

        // Top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8); setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        val back = mkTv("←", 22f, "#F9D423", true).apply {
            setPadding(8, 6, 16, 6)
            setOnClickListener {
                gameScr.visibility = View.GONE; homeScr.visibility = View.VISIBLE; engine.stop()
            }
        }
        top.addView(back)
        top.addView(
            mkTv("♟  দাবা", 18f, "#F9D423", true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )

        val sndBtn = mkTv("🔊", 18f, "#FFFFFF", false).apply {
            setPadding(8, 6, 8, 6)
            setOnClickListener { soundOn = !soundOn; text = if (soundOn) "🔊" else "🔇" }
        }
        top.addView(sndBtn)

        val flipBtn = mkTv("⇅", 20f, "#A78BFA", true).apply {
            setPadding(8, 6, 8, 6)
            setOnClickListener { boardView.flipBoard() }
        }
        top.addView(flipBtn)
        ll.addView(top)

        // AI bar
        val aiRow = buildPlayerBar(false)
        ll.addView(aiRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(8, 6, 8, 4) })

        // Status
        statusTv = mkTv("আপনার পালা — চাল দিন!", 13f, "#FFFFFF", false).apply {
            gravity = Gravity.CENTER; setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22FFFFFF"))
                setStroke(1, Color.parseColor("#33FFFFFF"))
                cornerRadius = 10f
            }
        }
        ll.addView(statusTv, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(8, 0, 8, 4) })

        // Board
        boardView = BoardView(this)
        boardView.onMove = { m, isCapture -> onPlayerMove(m, isCapture) }
        ll.addView(boardView, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // Player bar
        val playerRow = buildPlayerBar(true)
        ll.addView(playerRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(8, 4, 8, 4) })

        // Bottom buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(8, 0, 8, 10)
        }
        val newBtn = mkBtn("🔄 নতুন খেলা", "#667EEA", "#764BA2") { startGame() }
        val resBtn = mkBtn("🏳 হার মানুন", "#666666", "#444444") { resign() }
        btnRow.addView(newBtn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, 6, 0) })
        btnRow.addView(resBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        ll.addView(btnRow)

        return ll
    }

    private fun buildPlayerBar(isHuman: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 6, 12, 6)
            background = GradientDrawable().apply { setColor(Color.parseColor("#15FFFFFF")); cornerRadius = 10f }
        }
        val icon = mkTv(if (isHuman) "👤" else "🤖", 16f, "#FFFFFF", false)
        val name = mkTv(if (isHuman) "আপনি" else "Stockfish AI", 13f, "#FFFFFF", true)
        val diff = mkTv(if (!isHuman) "• কঠিনতা: $difficulty" else "", 11f, "#A78BFA", false)
        row.addView(icon)
        row.addView(name, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(8, 0, 0, 0) })
        row.addView(diff)
        return row
    }

    // =========================================================
    // WIN SCREEN
    // =========================================================
    private fun buildWin(): FrameLayout {
        val fl = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#DD000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A2E")); cornerRadius = 28f
                setStroke(2, Color.parseColor("#F9D423"))
            }
            elevation = 24f
        }
        card.addView(mkTv("🏆", 72f, "#F9D423", false).apply { gravity = Gravity.CENTER })
        winTv = mkTv("", 28f, "#F9D423", true).apply { gravity = Gravity.CENTER }
        card.addView(winTv)
        card.addView(mkTv("অসাধারণ খেলা!", 15f, "#A78BFA", false).apply {
            gravity = Gravity.CENTER; setPadding(0, 8, 0, 24)
        })
        card.addView(mkBtn("🔄  আবার খেলুন", "#F9D423", "#FF6B35") {
            winScr.visibility = View.GONE; startGame()
        })
        card.addView(mkBtn("🏠  হোম", "#667EEA", "#764BA2") {
            winScr.visibility = View.GONE
            gameScr.visibility = View.GONE
            homeScr.visibility = View.VISIBLE
        }.apply { setPadding(0, 10, 0, 0) })

        val flp = FrameLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER }
        fl.addView(card, flp)
        return fl
    }

    // =========================================================
    // GAME LOGIC
    // =========================================================
    private fun startGame() {
        homeScr.visibility = View.GONE; gameScr.visibility = View.VISIBLE; winScr.visibility = View.GONE
        boardView.game = ChessGame()
        boardView.playerColor = if (playerIsWhite) 'w' else 'b'
        boardView.flipped = !playerIsWhite
        boardView.refresh()
        engine.stop()
        exec.submit {
            engine.setSkill(difficulty)
            val ok = engine.init()
            handler.post {
                if (ok) {
                    statusTv.text = if (playerIsWhite) "সাদার পালা — আপনার চাল!" else "Stockfish ভাবছে..."
                    if (!playerIsWhite) aiMove()
                } else {
                    statusTv.text = "⚠️ Engine লোড হয়নি"
                }
            }
        }
    }

    private fun onPlayerMove(m: Move, isCapture: Boolean) {
        val g = boardView.game
        val inChk = g.inCheck(g.turn == 'w')
        playSound(isCapture, inChk)
        if (g.gameOver) { showWin(g.result); return }
        statusTv.text = "🤖 Stockfish ভাবছে..."
        handler.postDelayed({ aiMove() }, 300)
    }

    private fun aiMove() {
        val g = boardView.game
        if (g.gameOver || g.turn == boardView.playerColor) return
        val fen = g.toFen()
        val thinkMs = when (difficulty) {
            in 0..5 -> 500; in 6..12 -> 1200; in 13..17 -> 2000; else -> 3000
        }
        exec.submit {
            val best = engine.getBestMove(fen, g.moveHistory.toList(), thinkMs)
            handler.post {
                if (best != null) {
                    val m = g.parseUCI(best)
                    if (m != null) {
                        boardView.lastFrom = m.from; boardView.lastTo = m.to
                        val isCapture = g.board[m.to] != '.' || m.isEnPassant
                        val isCapAct = g.applyMove(m)
                        val inChk = g.inCheck(g.turn == 'w')
                        playSound(isCapAct, inChk)
                        boardView.invalidate()
                        if (g.gameOver) { showWin(g.result); return@post }
                        statusTv.text = if (boardView.playerColor == 'w') "⬜ আপনার পালা (সাদা)" else "⬛ আপনার পালা (কালো)"
                    }
                } else {
                    statusTv.text = "আপনার পালা!"
                }
            }
        }
    }

    private fun resign() {
        val result = if (playerIsWhite) "কালো জিতেছে! ♛\n(আপনি হার মেনেছেন)" else "সাদা জিতেছে! ♕\n(আপনি হার মেনেছেন)"
        showWin(result)
    }

    private fun showWin(result: String) {
        winTv.text = result; winScr.visibility = View.VISIBLE
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private fun mkTv(text: String, size: Float, color: String, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.parseColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun mkBtn(text: String, c1: String, c2: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD; setPadding(40, 16, 40, 16); elevation = 8f
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 50f
            colors = intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }
        setOnClickListener { onClick() }
    }

    override fun onDestroy() {
        super.onDestroy(); engine.stop(); exec.shutdownNow()
        mpMove?.release(); mpCapture?.release(); mpCheck?.release()
    }
}
