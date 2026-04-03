package com.daba.chess

data class Move(
    val from: Int, val to: Int,
    val promotion: Char = '.',
    val isEnPassant: Boolean = false,
    val isCastling: Boolean = false
) {
    fun toUCI(): String {
        val f = "abcdefgh"; val r = "87654321"
        val s = "${f[from%8]}${r[from/8]}${f[to%8]}${r[to/8]}"
        return if (promotion != '.') "$s${promotion.lowercaseChar()}" else s
    }
}

data class MoveBackup(
    val captured: Char, val epSq: Int, val castling: String,
    val halfMove: Int, val fullMove: Int, val turn: Char
)

class ChessGame {
    val board = CharArray(64) { '.' }
    var turn = 'w'
    var castling = "KQkq"
    var epSquare = -1
    var halfMoveClock = 0
    var fullMoveNumber = 1
    var gameOver = false
    var result = ""
    val moveHistory = mutableListOf<String>()

    init { reset() }

    fun reset() {
        setFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        moveHistory.clear(); gameOver = false; result = ""
    }

    fun setFen(fen: String) {
        board.fill('.')
        val parts = fen.trim().split(" ")
        var idx = 0
        for (ch in parts[0]) {
            when { ch == '/' -> {}; ch.isDigit() -> idx += ch.digitToInt(); else -> board[idx++] = ch }
        }
        turn = if (parts.size > 1) parts[1][0] else 'w'
        castling = if (parts.size > 2 && parts[2] != "-") parts[2] else ""
        epSquare = if (parts.size > 3 && parts[3] != "-") {
            val fi = parts[3][0] - 'a'; val ri = '8' - parts[3][1]; ri * 8 + fi
        } else -1
        halfMoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
        fullMoveNumber = parts.getOrNull(5)?.toIntOrNull() ?: 1
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var e = 0
            for (c in 0..7) {
                val pc = board[r*8+c]
                if (pc == '.') e++ else { if (e > 0) { sb.append(e); e = 0 }; sb.append(pc) }
            }
            if (e > 0) sb.append(e); if (r < 7) sb.append('/')
        }
        val ep = if (epSquare >= 0) "${'a'+epSquare%8}${'8'-epSquare/8}" else "-"
        val ca = if (castling.isEmpty()) "-" else castling
        return "$sb $turn $ca $ep $halfMoveClock $fullMoveNumber"
    }

    private fun isW(pc: Char) = pc.isUpperCase() && pc != '.'
    private fun isB(pc: Char) = pc.isLowerCase()
    private fun empty(sq: Int) = board[sq] == '.'
    private fun enemy(sq: Int, w: Boolean) = if(w) isB(board[sq]) else isW(board[sq])
    private fun friend(sq: Int, w: Boolean) = if(w) isW(board[sq]) else isB(board[sq])

    fun legalMoves(): List<Move> {
        val pseudo = mutableListOf<Move>()
        val w = turn == 'w'
        for (sq in 0..63) {
            val pc = board[sq]; if (pc == '.' || isW(pc) != w) continue
            when (pc.uppercaseChar()) {
                'P' -> pawnMoves(sq, w, pseudo)
                'N' -> knightMoves(sq, w, pseudo)
                'B' -> slideMoves(sq, w, pseudo, listOf(-9,-7,7,9))
                'R' -> slideMoves(sq, w, pseudo, listOf(-8,-1,1,8))
                'Q' -> slideMoves(sq, w, pseudo, listOf(-9,-8,-7,-1,1,7,8,9))
                'K' -> kingMoves(sq, w, pseudo)
            }
        }
        return pseudo.filter { legal(it) }
    }

    private fun pawnMoves(sq: Int, w: Boolean, out: MutableList<Move>) {
        val row = sq/8; val col = sq%8
        val dir = if(w) -1 else 1
        val startR = if(w) 6 else 1; val promR = if(w) 0 else 7
        val fwd = sq + dir*8
        if (fwd in 0..63 && empty(fwd)) {
            if (fwd/8 == promR) { for(p in "qrbn") out.add(Move(sq,fwd, if(w) p.uppercaseChar() else p)) }
            else {
                out.add(Move(sq,fwd))
                val dbl = sq+dir*16
                if (row == startR && empty(dbl)) out.add(Move(sq,dbl))
            }
        }
        for (dc in listOf(-1,1)) {
            if (col+dc !in 0..7) continue
            val cap = sq+dir*8+dc
            if (cap !in 0..63) continue
            if (enemy(cap,w)) {
                if (cap/8==promR) { for(p in "qrbn") out.add(Move(sq,cap,if(w)p.uppercaseChar() else p)) }
                else out.add(Move(sq,cap))
            } else if (cap == epSquare) out.add(Move(sq,cap,isEnPassant=true))
        }
    }

    private fun knightMoves(sq: Int, w: Boolean, out: MutableList<Move>) {
        val row=sq/8; val col=sq%8
        for ((dr,dc) in listOf(-2 to -1,-2 to 1,-1 to -2,-1 to 2,1 to -2,1 to 2,2 to -1,2 to 1)) {
            val nr=row+dr; val nc=col+dc
            if (nr in 0..7 && nc in 0..7) { val t=nr*8+nc; if(!friend(t,w)) out.add(Move(sq,t)) }
        }
    }

    private fun slideMoves(sq: Int, w: Boolean, out: MutableList<Move>, dirs: List<Int>) {
        val col = sq%8
        for (d in dirs) {
            var cur = sq
            while (true) {
                val nxt = cur+d; if (nxt !in 0..63) break
                val cc = cur%8; val nc = nxt%8
                if (Math.abs(d) == 1 && Math.abs(cc-nc) > 1) break
                if (Math.abs(d) in listOf(7,9) && Math.abs(cc-nc) > 1) break
                if (friend(nxt,w)) break
                out.add(Move(sq,nxt))
                if (enemy(nxt,w)) break
                cur = nxt
            }
        }
    }

    private fun kingMoves(sq: Int, w: Boolean, out: MutableList<Move>) {
        val row=sq/8; val col=sq%8
        for ((dr,dc) in listOf(-1 to -1,-1 to 0,-1 to 1,0 to -1,0 to 1,1 to -1,1 to 0,1 to 1)) {
            val nr=row+dr; val nc=col+dc
            if (nr in 0..7 && nc in 0..7) { val t=nr*8+nc; if(!friend(t,w)) out.add(Move(sq,t)) }
        }
        // Castling
        if (w && sq==60) {
            if ('K' in castling && empty(61) && empty(62) && board[63]=='R') out.add(Move(60,62,isCastling=true))
            if ('Q' in castling && empty(59) && empty(58) && empty(57) && board[56]=='R') out.add(Move(60,58,isCastling=true))
        } else if (!w && sq==4) {
            if ('k' in castling && empty(5) && empty(6) && board[7]=='r') out.add(Move(4,6,isCastling=true))
            if ('q' in castling && empty(3) && empty(2) && empty(1) && board[0]=='r') out.add(Move(4,2,isCastling=true))
        }
    }

    fun inCheck(w: Boolean): Boolean {
        val k = board.indexOfFirst { it == if(w) 'K' else 'k' }
        return k >= 0 && attacked(k, !w)
    }

    fun attacked(sq: Int, byW: Boolean): Boolean {
        val row=sq/8; val col=sq%8
        val pDir = if(byW) 1 else -1
        for (dc in listOf(-1,1)) {
            val pr=row+pDir; val pc=col+dc
            if (pr in 0..7 && pc in 0..7) {
                val p = board[pr*8+pc]
                if (byW && p=='P') return true; if (!byW && p=='p') return true
            }
        }
        for ((dr,dc) in listOf(-2 to -1,-2 to 1,-1 to -2,-1 to 2,1 to -2,1 to 2,2 to -1,2 to 1)) {
            val nr=row+dr; val nc=col+dc
            if (nr in 0..7 && nc in 0..7) {
                val p=board[nr*8+nc]
                if (byW && p=='N') return true; if (!byW && p=='n') return true
            }
        }
        for ((dr,dc) in listOf(-1 to -1,-1 to 1,1 to -1,1 to 1)) {
            var r=row+dr; var c=col+dc
            while (r in 0..7 && c in 0..7) {
                val p=board[r*8+c]; if (p != '.') {
                    val u=p.uppercaseChar()
                    if ((u=='B'||u=='Q') && (byW==isW(p))) return true; break
                }; r+=dr; c+=dc
            }
        }
        for ((dr,dc) in listOf(-1 to 0,1 to 0,0 to -1,0 to 1)) {
            var r=row+dr; var c=col+dc
            while (r in 0..7 && c in 0..7) {
                val p=board[r*8+c]; if (p != '.') {
                    val u=p.uppercaseChar()
                    if ((u=='R'||u=='Q') && (byW==isW(p))) return true; break
                }; r+=dr; c+=dc
            }
        }
        for ((dr,dc) in listOf(-1 to -1,-1 to 0,-1 to 1,0 to -1,0 to 1,1 to -1,1 to 0,1 to 1)) {
            val nr=row+dr; val nc=col+dc
            if (nr in 0..7 && nc in 0..7) {
                val p=board[nr*8+nc]
                if (byW && p=='K') return true; if (!byW && p=='k') return true
            }
        }
        return false
    }

    private fun legal(m: Move): Boolean {
        val bk = doMove(m); val w = bk.turn == 'w'
        val chk = inCheck(w); undoMove(m, bk); return !chk
    }

    fun doMove(m: Move): MoveBackup {
        val bk = MoveBackup(board[m.to], epSquare, castling, halfMoveClock, fullMoveNumber, turn)
        val pc = board[m.from]; val w = isW(pc)
        board[m.to] = if(m.promotion != '.') m.promotion else pc
        board[m.from] = '.'
        if (m.isEnPassant) board[m.to + if(w) 8 else -8] = '.'
        if (m.isCastling) when(m.to) {
            62 -> { board[61]='R'; board[63]='.' }; 58 -> { board[59]='R'; board[56]='.' }
            6  -> { board[5]='r'; board[7]='.'  }; 2  -> { board[3]='r'; board[0]='.'  }
        }
        epSquare = if (pc.uppercaseChar()=='P' && Math.abs(m.from-m.to)==16) (m.from+m.to)/2 else -1
        val nc = StringBuilder(castling)
        when(m.from) { 60->{nc.replace(Regex("[KQ]"),"")}; 56->{nc.deleteChars('Q')}; 63->{nc.deleteChars('K')}
                       4 ->{nc.replace(Regex("[kq]"),"")}; 0 ->{nc.deleteChars('k')}; 7->{nc.deleteChars('k')} }
        when(m.to) { 56->{nc.deleteChars('Q')}; 63->{nc.deleteChars('K')}; 0->{nc.deleteChars('k')}; 7->{nc.deleteChars('k')} }
        castling = nc.toString()
        halfMoveClock = if (pc.uppercaseChar()=='P'||bk.captured!='.') 0 else halfMoveClock+1
        if (!w) fullMoveNumber++
        turn = if(w) 'b' else 'w'
        return bk
    }

    private fun StringBuilder.deleteChars(c: Char) { val i=indexOf(c.toString()); if(i>=0) deleteCharAt(i) }

    fun undoMove(m: Move, bk: MoveBackup) {
        val pc = board[m.to]; val w = bk.turn == 'w'
        board[m.from] = if(m.promotion!='.') (if(w) 'P' else 'p') else pc
        board[m.to] = bk.captured
        if (m.isEnPassant) board[m.to + if(w) 8 else -8] = if(w) 'p' else 'P'
        if (m.isCastling) when(m.to) {
            62->{board[63]='R';board[61]='.'}; 58->{board[56]='R';board[59]='.'}
            6->{board[7]='r';board[5]='.'}; 2->{board[0]='r';board[3]='.'}
        }
        epSquare=bk.epSq; castling=bk.castling; halfMoveClock=bk.halfMove
        fullMoveNumber=bk.fullMove; turn=bk.turn
    }

    fun applyMove(m: Move): Boolean {
        val isCapture = board[m.to] != '.' || m.isEnPassant
        doMove(m)
        moveHistory.add(m.toUCI())
        val lm = legalMoves()
        if (lm.isEmpty()) {
            gameOver = true
            result = if (inCheck(turn=='w')) {
                if (turn=='w') "কালো জিতেছে! ♛" else "সাদা জিতেছে! ♕"
            } else "ড্র! (স্টেলমেট)"
        } else if (halfMoveClock >= 100) { gameOver=true; result="ড্র! (৫০ চাল নিয়ম)" }
        return isCapture
    }

    fun parseUCI(uci: String): Move? {
        if (uci.length < 4) return null
        val fi = "abcdefgh"
        val ff = fi.indexOf(uci[0]); val fr = '8'-uci[1]
        val tf = fi.indexOf(uci[2]); val tr = '8'-uci[3]
        if (ff<0||tf<0) return null
        val from=fr*8+ff; val to=tr*8+tf
        val prom = if (uci.length==5) { val p=uci[4]; if(turn=='w') p.uppercaseChar() else p } else '.'
        val pc=board[from]
        val isEP = pc.uppercaseChar()=='P' && to==epSquare && Math.abs(ff-tf)==1 && board[to]=='.'
        val isCastle = pc.uppercaseChar()=='K' && Math.abs(ff-tf)==2
        return Move(from,to,prom,isEP,isCastle)
    }
}
