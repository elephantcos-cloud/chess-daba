package com.daba.chess

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var game = ChessGame()
    var playerColor = 'w'   // which side human plays
    var onMove: ((Move, Boolean) -> Unit)? = null  // move, isCapture
    var onPromotion: ((Int, Int) -> Unit)? = null
    var legalMoves = listOf<Move>()
    var selectedSq = -1
    var lastFrom = -1; var lastTo = -1
    var promotionPending: Pair<Int,Int>? = null
    var flipped = false

    private var cs = 0f   // cell size
    private var bx = 0f; private var by = 0f  // board origin

    // Colors - Lichess style
    private val lightCol = Color.parseColor("#F0D9B5")
    private val darkCol  = Color.parseColor("#B58863")
    private val selLight = Color.parseColor("#CDD16E")
    private val selDark  = Color.parseColor("#AABA44")
    private val lastLight= Color.parseColor("#CDD16E")
    private val lastDark = Color.parseColor("#AABA44")
    private val checkCol = Color.parseColor("#CC0000")
    private val coordCol = Color.parseColor("#A07040")

    private val fill  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val piece = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(3f, 1.5f, 1.5f, Color.parseColor("#88000000"))
    }
    private val coord = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD
    }
    private val moveDot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moveRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gradBg = Paint(Paint.ANTI_ALIAS_FLAG)

    private val PIECES = mapOf(
        'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘", 'P' to "♙",
        'k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟"
    )

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cs = min(w, h) / 8f
        bx = (w - cs * 8) / 2f
        by = (h - cs * 8) / 2f
        piece.textSize = cs * 0.78f
        coord.textSize = cs * 0.22f
        coord.color = coordCol
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Background
        val bgShader = LinearGradient(0f,0f,width.toFloat(),height.toFloat(),
            intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#302B63")),
            null, Shader.TileMode.CLAMP)
        gradBg.shader = bgShader
        canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(), gradBg)

        // Board shadow
        fill.color = Color.parseColor("#88000000")
        fill.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRect(bx+6f, by+6f, bx+cs*8+6f, by+cs*8+6f, fill)
        fill.maskFilter = null

        // Draw squares
        for (r in 0..7) for (c in 0..7) drawSquare(canvas, r, c)

        // Coord labels
        for (i in 0..7) {
            val file = if(flipped) ('h'-i).toString() else ('a'+i).toString()
            val rank = if(flipped) (i+1).toString() else (8-i).toString()
            coord.textAlign = Paint.Align.CENTER
            canvas.drawText(file, bx+cs*i+cs/2, by+cs*8+cs*0.3f, coord)
            coord.textAlign = Paint.Align.RIGHT
            canvas.drawText(rank, bx-cs*0.08f, by+cs*i+cs*0.35f, coord)
        }

        // Promotion overlay
        promotionPending?.let { drawPromotionMenu(canvas, it.first, it.second) }
    }

    private fun drawSquare(canvas: Canvas, r: Int, c: Int) {
        val sq = if(flipped) (7-r)*8+(7-c) else r*8+c
        val isLight = (r+c)%2 == 0
        val x = bx + c*cs; val y = by + r*cs
        val rect = RectF(x, y, x+cs, y+cs)

        // Square color
        val baseCol = when {
            sq == selectedSq -> if(isLight) selLight else selDark
            sq == lastFrom || sq == lastTo -> if(isLight) lastLight else lastDark
            else -> if(isLight) lightCol else darkCol
        }
        fill.color = baseCol
        canvas.drawRect(rect, fill)

        // Check highlight
        val kSq = game.board.indexOfFirst { it == if(game.turn=='w') 'K' else 'k' }
        if (sq == kSq && game.inCheck(game.turn=='w')) {
            val rad = RadialGradient(x+cs/2,y+cs/2,cs*0.6f,
                intArrayOf(Color.parseColor("#CCFF0000"),Color.parseColor("#00FF0000")),
                null, Shader.TileMode.CLAMP)
            fill.shader = rad; canvas.drawRect(rect, fill); fill.shader = null
        }

        // Legal move highlight
        if (legalMoves.any { it.to == sq }) {
            if (game.board[sq] != '.') {
                moveRing.color = Color.parseColor("#99000000")
                moveRing.strokeWidth = cs*0.1f
                canvas.drawCircle(x+cs/2,y+cs/2,cs*0.46f,moveRing)
            } else {
                moveDot.color = Color.parseColor("#55000000")
                canvas.drawCircle(x+cs/2,y+cs/2,cs*0.16f,moveDot)
            }
        }

        // Piece
        val pc = game.board[sq]
        if (pc != '.') {
            val sym = PIECES[pc] ?: return
            piece.color = if(pc.isUpperCase()) Color.WHITE else Color.parseColor("#1A1A1A")
            canvas.drawText(sym, x+cs/2, y+cs*0.82f, piece)
        }
    }

    private fun drawPromotionMenu(canvas: Canvas, col: Int, isWhite: Int) {
        val promos = if(isWhite == 1) listOf('Q','R','B','N') else listOf('q','r','b','n')
        val row = if(isWhite == 1) 0 else 7
        val dc = if(!flipped) col else 7-col

        fill.color = Color.parseColor("#EE1A1A2E")
        val px = bx+dc*cs; val py = by
        canvas.drawRoundRect(RectF(px,py,px+cs,py+cs*4), cs*0.1f,cs*0.1f,fill)

        for ((i,p) in promos.withIndex()) {
            val r = if(isWhite==1) i else 7-i
            val sym = PIECES[p] ?: continue
            piece.color = if(p.isUpperCase()) Color.WHITE else Color.parseColor("#1A1A1A")
            fill.color = if(i%2==0) Color.parseColor("#55FFFFFF") else Color.TRANSPARENT
            canvas.drawRect(px,by+r*cs,px+cs,by+(r+1)*cs,fill)
            canvas.drawText(sym,px+cs/2,by+r*cs+cs*0.82f,piece)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_UP) return true
        val tx = ev.x - bx; val ty = ev.y - by
        if (tx < 0 || ty < 0 || tx > cs*8 || ty > cs*8) return true
        val vc = (tx/cs).toInt(); val vr = (ty/cs).toInt()
        val sq = if(flipped) (7-vr)*8+(7-vc) else vr*8+vc

        // Promotion menu active
        promotionPending?.let { (col, isW) ->
            val dc = if(!flipped) col else 7-col
            if (vc == dc) {
                val promos = if(isW==1) listOf('Q','R','B','N') else listOf('q','r','b','n')
                val row = if(isW==1) vr else 7-vr
                if (row in 0..3) {
                    val chosen = promos[row]
                    val (from, to) = it.first to sq
                    val m = Move(from, it.first*8 + col, chosen) // Need proper reconstruction
                    // Find the promotion move
                    val pm = legalMoves.find { mv ->
                        mv.from == from && mv.to == (if(isW==1) col else 56+col) && mv.promotion == chosen
                    }
                    if (pm != null) { promotionPending = null; executeMove(pm) }
                }
            } else { promotionPending = null; selectedSq = -1; legalMoves = listOf() }
            invalidate(); return true
        }

        if (game.turn != playerColor || game.gameOver) return true

        if (selectedSq >= 0) {
            val mv = legalMoves.find { it.to == sq }
            if (mv != null) {
                // Check if promotion needed
                val pc = game.board[selectedSq]
                val isPromRow = if(playerColor=='w') sq/8==0 else sq/8==7
                if (pc.uppercaseChar()=='P' && isPromRow) {
                    // Show promotion menu
                    promotionPending = Pair(selectedSq, if(playerColor=='w') 1 else 0)
                    invalidate(); return true
                }
                executeMove(mv)
            } else {
                // Select different piece or deselect
                if (!game.board[sq].equals('.') && (game.board[sq].isUpperCase() == (playerColor=='w'))) {
                    selectedSq = sq
                    legalMoves = game.legalMoves().filter { it.from == sq }
                } else { selectedSq = -1; legalMoves = listOf() }
            }
        } else {
            if (!game.board[sq].equals('.') && (game.board[sq].isUpperCase() == (playerColor=='w'))) {
                selectedSq = sq
                legalMoves = game.legalMoves().filter { it.from == sq }
            }
        }
        invalidate(); return true
    }

    fun executeMove(m: Move) {
        lastFrom = m.from; lastTo = m.to
        selectedSq = -1; legalMoves = listOf()
        val isCapture = game.applyMove(m)
        onMove?.invoke(m, isCapture)
        invalidate()
    }

    fun flipBoard() { flipped = !flipped; invalidate() }
    fun refresh() { selectedSq=-1; legalMoves=listOf(); lastFrom=-1; lastTo=-1; invalidate() }
}
