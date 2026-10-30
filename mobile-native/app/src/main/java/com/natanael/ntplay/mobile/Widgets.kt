package com.natanael.ntplay.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Campo de busca/filtro, igual em todas as telas que peneiram uma lista. */
@Composable
fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        placeholder = { Text(hint, color = Muted, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardC,
            unfocusedContainerColor = CardC,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Cyan,
            focusedIndicatorColor = Purple,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Fita de categorias que rola na horizontal. "Todos" é a primeira e significa sem filtro.
 */
@Composable
fun CategoryStrip(cats: List<String>, selected: String, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Chip("Todos", selected.isEmpty()) { onPick("") } }
        items(cats) { c -> Chip(c, selected == c) { onPick(c) } }
    }
}

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) Purple else Btn)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (on) Color.White else Muted,
        fontSize = 12.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
    )
}

/** Título de prateleira, com a contagem do lado. */
@Composable
fun ShelfTitle(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (count > 0) {
            Text("  $count", color = Muted, fontSize = 11.sp)
        }
    }
}
