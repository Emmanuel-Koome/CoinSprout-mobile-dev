package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.*

private data class Article(val emoji: String, val title: String, val readTime: String, val summary: String)

@Composable
fun EducationScreen(navController: NavHostController) {
    val articles = listOf(
        Article("💡", "What is a Money Market Fund (MMF)?", "3 min read", "Learn how MMFs offer high liquidity while earning steady annual interest above inflation in Kenya."),
        Article("🏛️", "Understanding Central Bank T-Bills", "4 min read", "Treasury Bills are short-term government debt securities backed by CBK, providing safe guaranteed yields."),
        Article("🌱", "The Power of Micro-Compounding", "2 min read", "How saving spare change of KES 15 per transaction adds up to tens of thousands over 12 months.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Financial Literacy", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Text("Empowering East African savers with financial clarity.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Spacer(Modifier.height(16.dp))

        articles.forEach { article ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Paper),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${article.emoji} ${article.title}", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(article.readTime, style = MaterialTheme.typography.bodySmall, color = Sprout)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(article.summary, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }
        }
    }
}
