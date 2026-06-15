package com.looper.base.screen.dialog.child

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.looper.base.R
import com.looper.base.base.ui.stringResourceCustom
import com.looper.base.ui.theme.Color171717
import com.looper.base.ui.theme.Color38B000
import com.looper.base.ui.theme.ColorFFFFFF
import com.looper.base.ui.theme.Typography
import com.looper.base.ui.theme.titleSemiBold

@Composable
fun NoInternetDialog(
    onOpenSettings: () -> Unit
) {

    Dialog(onDismissRequest = {}) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color171717,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Image(
                    painter = painterResource(R.drawable.ic_network_error),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResourceCustom(R.string.oops_connection_lost),
                    style = Typography.titleSemiBold.copy(
                        color = ColorFFFFFF,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W500
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResourceCustom(R.string.this_app_requires_an_internet_connection_please_connect_to_wi_fi_or_mobile_data),
                    style = Typography.titleSemiBold.copy(
                        color = ColorFFFFFF,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(Color38B000)
                ) {
                    Text(
                        text = stringResourceCustom(R.string.open_settings),
                        style = Typography.titleSemiBold.copy(
                            color = ColorFFFFFF,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W500
                        )
                    )
                }
            }
        }
    }
}