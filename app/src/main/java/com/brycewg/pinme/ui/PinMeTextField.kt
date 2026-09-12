package com.brycewg.pinme.ui

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * 用系统 EditText 承载 IME。同一 Window 内切焦点走 requestFocus，
 * 不会拆掉 Compose PlatformTextInputSession。
 */
@Composable
fun PinMeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
) {
    var focused by remember { mutableStateOf(false) }
    val valueChangeState = rememberUpdatedState(onValueChange)
    val shape = RoundedCornerShape(16.dp)
    val minHeight = if (singleLine) 52.dp else 72.dp

    Row(
        modifier =
            modifier
                .clip(shape)
                .background(colors.backgroundColor)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) colors.borderColor else colors.backgroundColor,
                    shape = shape,
                )
                .heightIn(min = minHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            factory = { context ->
                EditText(context).apply {
                    background = null
                    setPadding(0, 0, 0, 0)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    includeFontPadding = false
                    setImeOptions(
                        EditorInfo.IME_FLAG_NO_FULLSCREEN or
                            EditorInfo.IME_FLAG_NO_EXTRACT_UI,
                    )
                    doAfterTextChanged { text ->
                        valueChangeState.value(text?.toString().orEmpty())
                    }
                    setOnFocusChangeListener { _, hasFocus ->
                        focused = hasFocus
                    }
                    setOnEditorActionListener { view, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_NEXT) {
                            view.focusSearch(View.FOCUS_DOWN)?.requestFocus() != null
                        } else {
                            false
                        }
                    }
                }
            },
            update = { view ->
                view.isEnabled = enabled
                view.hint = label
                view.setHintTextColor(colors.labelColor.copy(alpha = 0.7f).toArgb())
                view.setTextColor(colors.labelColor.toArgb())
                view.isSingleLine = singleLine
                view.minLines = minLines.coerceAtLeast(1)
                view.maxLines = maxLines.coerceIn(1, 20)
                view.inputType =
                    if (singleLine) {
                        InputType.TYPE_CLASS_TEXT
                    } else {
                        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    }
                val action =
                    when (keyboardOptions.imeAction) {
                        ImeAction.Done -> EditorInfo.IME_ACTION_DONE
                        ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
                        ImeAction.Go -> EditorInfo.IME_ACTION_GO
                        ImeAction.Send -> EditorInfo.IME_ACTION_SEND
                        else -> if (singleLine) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_NONE
                    }
                view.imeOptions =
                    EditorInfo.IME_FLAG_NO_FULLSCREEN or
                        EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                        action
                // inputType / isSingleLine 会重置 transformationMethod，必须放在它们之后
                val password = visualTransformation is PasswordVisualTransformation
                val wantMethod = if (password) PasswordTransformationMethod.getInstance() else null
                if (view.transformationMethod != wantMethod) {
                    val start = view.selectionStart
                    val end = view.selectionEnd
                    view.transformationMethod = wantMethod
                    val len = view.text.length
                    if (len > 0) {
                        view.setSelection(start.coerceIn(0, len), end.coerceIn(0, len))
                    }
                }
                if (view.text.toString() != value) {
                    val start = view.selectionStart
                    val end = view.selectionEnd
                    view.setText(value, TextView.BufferType.EDITABLE)
                    val len = view.text.length
                    view.setSelection(start.coerceIn(0, len), end.coerceIn(0, len))
                }
            },
        )
        trailingIcon?.invoke()
    }
}
