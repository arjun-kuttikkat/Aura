package com.aura.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.app.R

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(id = R.drawable.logo_app),
        contentDescription = "Aura Logo",
        modifier = modifier.size(size),
        contentScale = contentScale,
    )
}
