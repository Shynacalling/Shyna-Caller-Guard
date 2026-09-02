package com.example.callruleblocker.ui

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object DialerState {
    var textFieldValue by mutableStateOf(TextFieldValue(""))

    val number: String get() = textFieldValue.text

    fun update(newValue: TextFieldValue) {
        textFieldValue = newValue
    }

    fun append(digit: String) {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        if (text.length < 100) {
            val newText = text.substring(0, selection.start) + digit + text.substring(selection.end)
            val newSelection = TextRange(selection.start + digit.length)
            textFieldValue = TextFieldValue(newText, newSelection)
        }
    }

    fun delete() {
        val text = textFieldValue.text
        val selection = textFieldValue.selection
        if (selection.collapsed) {
            if (selection.start > 0) {
                val newText = text.substring(0, selection.start - 1) + text.substring(selection.start)
                val newSelection = TextRange(selection.start - 1)
                textFieldValue = TextFieldValue(newText, newSelection)
            }
        } else {
            val newText = text.substring(0, selection.start) + text.substring(selection.end)
            val newSelection = TextRange(selection.start)
            textFieldValue = TextFieldValue(newText, newSelection)
        }
    }

    fun clear() {
        textFieldValue = TextFieldValue("", TextRange.Zero)
    }
}
