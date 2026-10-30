package com.natanael.ntplay.mobile

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.natanael.ntplay.core.AppState

const val WHATS_NUM = "(42) 92002-8569"
const val WHATS_URL = "https://wa.me/5542920028569"

/** QR code como imagem. Devolve null se o ZXing falhar — a tela continua utilizável sem ele. */
fun qrBitmap(text: String, size: Int = 360): ImageBitmap? = try {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp.asImageBitmap()
} catch (e: Throwable) {
    null
}

/**
 * Tela de entrada. Sem conta válida, nada do app fica acessível — mesma regra da TV desde a 1.8.0.
 * Contas são criadas só no painel, então aqui não existe cadastro: quem não tem pede pelo WhatsApp.
 */
@Composable
fun LoginGate(st: AppState) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val qr = remember { qrBitmap(WHATS_URL) }

    fun entrar() = st.accountLogin(user, pass)

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.logo), "NTPlay",
                Modifier.size(140.dp), contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(8.dp))
            Text("Entre com sua conta", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                user, { user = it.trim() },
                Modifier.fillMaxWidth(),
                label = { Text("Usuário") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                pass, { pass = it },
                Modifier.fillMaxWidth(),
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { entrar() }),
            )

            if (st.accessMsg.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    st.accessMsg, color = Danger, fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                { entrar() },
                Modifier.fillMaxWidth().height(52.dp),
                enabled = !st.syncBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) {
                if (st.syncBusy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Entrar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(36.dp))
            Text(
                "Peça seu teste do aplicativo ou da lista",
                color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            if (qr != null) {
                Image(qr, "QR do WhatsApp", Modifier.size(132.dp).background(androidx.compose.ui.graphics.Color.White).padding(6.dp))
                Spacer(Modifier.height(8.dp))
            }
            Text("WhatsApp $WHATS_NUM", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
