package app.opentv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Shared search input: prevent the landscape IME from opening its full-screen editor. */
@Composable
fun NexusSearchField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, hint: String = "Pesquisar…") {
    val onChange = rememberUpdatedState(onValueChange)
    AndroidView(modifier = modifier.fillMaxWidth().height(48.dp), factory = { context ->
        EditText(context).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_FILTER
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            val density = resources.displayMetrics.density
            setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
            background = GradientDrawable().apply {
                setColor(Color.rgb(23, 23, 23))
                cornerRadius = 8 * density
                setStroke((density).toInt().coerceAtLeast(1), Color.rgb(229, 196, 101))
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange.value(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: Editable?) {}
            })
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) {
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
                    clearFocus()
                    true
                } else false
            }
        }
    }, update = { field ->
        field.hint = hint
        if (field.text.toString() != value) { field.setText(value); field.setSelection(value.length) }
    })
}
