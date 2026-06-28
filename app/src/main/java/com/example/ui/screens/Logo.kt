package com.example.ui.screens
 
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun MusiclyLogo(
    modifier: Modifier = Modifier,
    isCircleBackground: Boolean = true,
    noteColor: Color = Color.White,
    backgroundColor: Color? = null
) {
    if (isCircleBackground) {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(backgroundColor ?: Color(0xFF0C100D)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.img_app_logo),
                contentDescription = "Musicly Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.1f)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Image(
            painter = painterResource(id = com.example.R.drawable.img_app_logo),
            contentDescription = "Musicly Logo",
            modifier = modifier.scale(1.1f),
            contentScale = ContentScale.Crop
        )
    }
}



