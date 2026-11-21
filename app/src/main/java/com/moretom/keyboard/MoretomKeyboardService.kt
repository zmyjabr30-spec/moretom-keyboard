package com.moretom.keyboard

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout

class MoretomKeyboardService : InputMethodService() {

    private var isShifted = false
    private var isCapsLock = false
    private var isSymbols = false
    private var useUnicodeRtlMark = false
    private var lastShiftClickTime = 0L

    private lateinit var rootLayout: LinearLayout
    private val letterButtons = mutableListOf<Button>()
    private var shiftButton: Button? = null
    private var symButton: Button? = null
    private var rtlToggleButton: Button? = null

    private val repeatHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            performRtlBackspace()
            repeatHandler.postDelayed(this, 65)
        }
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(4), dp(6), dp(4), dp(8))
        }
        buildKeyboard()
        return rootLayout
    }

    private fun buildKeyboard() {
        rootLayout.removeAllViews()
        letterButtons.clear()

        // Top utility strip: Number row + Unicode RTL Override toggle
        val numRow = createRow()
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        for (num in numbers) {
            numRow.addView(createKeyButton(num, weight = 1f, isSpecial = false) {
                commitRtlText(num)
            })
        }
        rtlToggleButton = createKeyButton(
            if (useUnicodeRtlMark) "RLO:ON" else "RLO",
            weight = 1.3f,
            isSpecial = true
        ) {
            useUnicodeRtlMark = !useUnicodeRtlMark
            rtlToggleButton?.text = if (useUnicodeRtlMark) "RLO:ON" else "RLO"
            if (useUnicodeRtlMark) {
                currentInputConnection?.commitText("\u202E", 1)
            }
        }
        numRow.addView(rtlToggleButton)
        rootLayout.addView(numRow)

        if (!isSymbols) {
            // QWERTY Rows
            val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

            val r1Layout = createRow()
            for (ch in row1) {
                val btn = createKeyButton(formatChar(ch), weight = 1f, isSpecial = false) {
                    onLetterPressed(ch)
                }
                letterButtons.add(btn)
                r1Layout.addView(btn)
            }
            rootLayout.addView(r1Layout)

            val r2Layout = createRow()
            r2Layout.setPadding(dp(16), 0, dp(16), 0)
            for (ch in row2) {
                val btn = createKeyButton(formatChar(ch), weight = 1f, isSpecial = false) {
                    onLetterPressed(ch)
                }
                letterButtons.add(btn)
                r2Layout.addView(btn)
            }
            rootLayout.addView(r2Layout)

            val r3Layout = createRow()
            shiftButton = createKeyButton(getShiftLabel(), weight = 1.5f, isSpecial = true) {
                val now = System.currentTimeMillis()
                if (now - lastShiftClickTime < 350) {
                    isCapsLock = !isCapsLock
                    isShifted = isCapsLock
                } else {
                    if (isCapsLock) {
                        isCapsLock = false
                        isShifted = false
                    } else {
                        isShifted = !isShifted
                    }
                }
                lastShiftClickTime = now
                updateLetterLabels()
            }
            r3Layout.addView(shiftButton)

            for (ch in row3) {
                val btn = createKeyButton(formatChar(ch), weight = 1f, isSpecial = false) {
                    onLetterPressed(ch)
                }
                letterButtons.add(btn)
                r3Layout.addView(btn)
            }

            r3Layout.addView(createBackspaceButton(weight = 1.5f))
            rootLayout.addView(r3Layout)
        } else {
            // Symbols Layout
            val sym1 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
            val sym2 = listOf("*", "\"", "'", ":", ";", "!", "?", "~", "`")
            val sym3 = listOf("[", "]", "{", "}", "<", ">", "\\", "|", "=")

            val s1Layout = createRow()
            for (ch in sym1) {
                s1Layout.addView(createKeyButton(ch, weight = 1f, isSpecial = false) {
                    commitRtlText(ch)
                })
            }
            rootLayout.addView(s1Layout)

            val s2Layout = createRow()
            s2Layout.setPadding(dp(12), 0, dp(12), 0)
            for (ch in sym2) {
                s2Layout.addView(createKeyButton(ch, weight = 1f, isSpecial = false) {
                    commitRtlText(ch)
                })
            }
            rootLayout.addView(s2Layout)

            val s3Layout = createRow()
            for (ch in sym3) {
                s3Layout.addView(createKeyButton(ch, weight = 1f, isSpecial = false) {
                    commitRtlText(ch)
                })
            }
            s3Layout.addView(createBackspaceButton(weight = 1.4f))
            rootLayout.addView(s3Layout)
        }

        // Bottom Row: ?123/ABC | , | Moretom (Spacebar - long press to switch keyboard) | . | Enter
        val bottomRow = createRow()

        symButton = createKeyButton(if (isSymbols) "ABC" else "?123", weight = 1.4f, isSpecial = true) {
            isSymbols = !isSymbols
            buildKeyboard()
        }
        bottomRow.addView(symButton)

        bottomRow.addView(createKeyButton(",", weight = 1f, isSpecial = false) {
            commitRtlText(",")
        })

        val spaceButton = createKeyButton("Moretom", weight = 4.2f, isSpecial = false) {
            commitRtlText(" ")
        }.apply {
            setOnLongClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
                true
            }
        }
        bottomRow.addView(spaceButton)

        bottomRow.addView(createKeyButton(".", weight = 1f, isSpecial = false) {
            commitRtlText(".")
        })

        bottomRow.addView(createKeyButton("↵", weight = 1.4f, isSpecial = true) {
            val ic = currentInputConnection ?: return@createKeyButton
            val action = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
            if ((action and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
                ic.performEditorAction(action and EditorInfo.IME_MASK_ACTION)
            } else {
                ic.commitText("\n", 1)
                if (useUnicodeRtlMark) {
                    ic.commitText("\u202E", 1)
                }
            }
        })

        rootLayout.addView(bottomRow)
    }

    private fun onLetterPressed(baseChar: String) {
        val output = formatChar(baseChar)
        commitRtlText(output)
        if (isShifted && !isCapsLock) {
            isShifted = false
            updateLetterLabels()
        }
    }

    private fun commitRtlText(text: String) {
        val ic = currentInputConnection ?: return
        if (useUnicodeRtlMark) {
            // Standard order inside Unicode RLO (U+202E) paragraph
            ic.commitText(text, 1)
        } else {
            // Pure RTL character placement: keeps cursor to the LEFT of newly typed character
            ic.commitText(text, 0)
        }
    }

    private fun performRtlBackspace() {
        val ic = currentInputConnection ?: return
        if (useUnicodeRtlMark) {
            ic.deleteSurroundingText(1, 0)
        } else {
            // Deletes 1 character to the RIGHT of the cursor
            ic.deleteSurroundingText(0, 1)
        }
    }

    private fun formatChar(ch: String): String {
        return if (isShifted || isCapsLock) ch.uppercase() else ch.lowercase()
    }

    private fun getShiftLabel(): String {
        return when {
            isCapsLock -> "⇪"
            isShifted -> "⬆"
            else -> "⇧"
        }
    }

    private fun updateLetterLabels() {
        shiftButton?.text = getShiftLabel()
        for (btn in letterButtons) {
            val current = btn.text.toString()
            btn.text = if (isShifted || isCapsLock) current.uppercase() else current.lowercase()
        }
    }

    private fun createRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        }
    }

    private fun createKeyButton(
        label: String,
        weight: Float,
        isSpecial: Boolean,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 2) 13f else 17f)
            setBackgroundResource(if (isSpecial) R.drawable.key_special_bg else R.drawable.key_bg)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createBackspaceButton(weight: Float): Button {
        return createKeyButton("⌫", weight, isSpecial = true) {
            performRtlBackspace()
        }.apply {
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        performRtlBackspace()
                        repeatHandler.postDelayed(deleteRunnable, 400)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        repeatHandler.removeCallbacks(deleteRunnable)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
