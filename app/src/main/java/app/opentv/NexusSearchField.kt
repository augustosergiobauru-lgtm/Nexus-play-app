package app.opentv

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Explicit colors keep input readable even when hosted outside the dark theme. */
@Composable
fun NexusSearchField(value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, hint: String = "Pesquisar…") {
    val gold = Color(0xFFE5C465)
    OutlinedTextField(value = value, onValueChange = onValueChange,
        placeholder = { Text(hint, color = Color.LightGray, fontSize = 13.sp) },
        singleLine = true, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = gold, focusedBorderColor = gold, unfocusedBorderColor = Color(0xFF73633B),
            focusedContainerColor = Color(0xFF171717), unfocusedContainerColor = Color(0xFF171717)))
}
