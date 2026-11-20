package app.opentv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val HomeGold = Color(0xFFE5C465)
private val EpisodeSuffix = Regex("\\s+S\\d+E\\d+.*$", RegexOption.IGNORE_CASE)
internal fun homeTitle(item: NexusCatalog.Item): String =
    item.title.replace(EpisodeSuffix, "").replace(Regex("\\s*\\[L]\\s*"), " ").trim()

/** A single artwork-led dashboard. Playback and branding stay in their existing components. */
@Composable
internal fun NexusHomeDashboard(
    items: List<NexusCatalog.Item>, loading: Boolean, failed: Boolean, busy: Boolean,
    onRetry: () -> Unit, onOpen: (NexusCatalog.Item) -> Unit, onBrowse: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val movies = items.filter { it.type == "movie" }.distinctBy { homeTitle(it).lowercase() }
    val series = items.filter { it.type == "series" }.distinctBy { homeTitle(it).lowercase() }
    val highlights = (0 until minOf(5, maxOf(movies.size, series.size))).flatMap { i ->
        listOfNotNull(movies.getOrNull(i), series.getOrNull(i))
    }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    if (highlights.isEmpty()) {
        if (loading) {
            Box(Modifier.fillMaxWidth().height(if (landscape) 182.dp else 300.dp)
                .clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF29251C), Color(0xFF131313)))),
                contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HomeGold, modifier = Modifier.size(26.dp)) }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF191713)).padding(24.dp)) {
                Text(if (failed) "Não foi possível atualizar os destaques" else "Seu catálogo está sendo preparado", color = Color.White, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (failed) "Tente carregar novamente." else "Os títulos disponíveis aparecerão aqui.", color = Color.LightGray)
                TextButton(onClick = onRetry) { Text("Tentar novamente", color = HomeGold) }
            }
        }
        return
    }
    BoxWithConstraints(modifier) {
    // The parent supplies the remaining height after branding and navigation.
    val heroHeight = if (landscape) maxHeight * .48f else 340.dp
    val railHeight = if (landscape) maxHeight - heroHeight - 12.dp else 230.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (landscape) 2.dp else 10.dp)) {
    val pager = rememberPagerState(pageCount = { highlights.size })
    HorizontalPager(state = pager, key = { highlights[it].id }, modifier = Modifier.fillMaxWidth().testTag("home-highlights")) { index ->
        val item = highlights[index]
        Box(Modifier.fillMaxWidth().height(heroHeight)
            .clip(RoundedCornerShape(16.dp)).background(Color(0xFF1B1A17))) {
            AsyncImage(model = item.logoUrl, contentDescription = null,
                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(if (landscape) .64f else 1f).align(Alignment.CenterEnd))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
                Color(0xFF0C0C0C), Color(0xFF0C0C0C).copy(alpha = .94f),
                Color(0xFF0C0C0C).copy(alpha = .34f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0C0C0C).copy(alpha = .8f)))))
            Column(Modifier.fillMaxHeight().fillMaxWidth(if (landscape) .60f else .88f).padding(if (landscape) 8.dp else 20.dp),
                verticalArrangement = Arrangement.Center) {
                Text(if (item.type == "series") "SÉRIE EM DESTAQUE" else "FILME EM DESTAQUE",
                    color = HomeGold, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(if (landscape) 2.dp else 8.dp))
                Text(homeTitle(item), color = Color.White, fontSize = if (landscape) 18.sp else 30.sp,
                    lineHeight = if (landscape) 21.sp else 34.sp, fontWeight = FontWeight.Bold,
                    maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("featured-title-$index"))
                if (!item.synopsis.isNullOrBlank()) {
                    Spacer(Modifier.height(if (landscape) 2.dp else 6.dp))
                    Text(item.synopsis, color = Color(0xFFD0D0D0), fontSize = 12.sp, maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(if (landscape) 2.dp else 12.dp))
                Button(onClick = { onOpen(item) }, enabled = !busy, modifier = if (landscape) Modifier.height(30.dp) else Modifier,
                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HomeGold, contentColor = Color.Black)) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (item.type == "series") "Ver série" else "Assistir", fontWeight = FontWeight.SemiBold)
                }
            }
            Text("${index + 1} / ${highlights.size}", color = Color.White.copy(alpha = .8f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        highlights.indices.forEach { index ->
            Box(Modifier.padding(horizontal = 3.dp).width(if (pager.currentPage == index) 18.dp else 5.dp).height(4.dp)
                .clip(RoundedCornerShape(50)).background(if (pager.currentPage == index) HomeGold else Color(0xFF444444)))
        }
    }
    if (failed && !landscape) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Alguns títulos não foram atualizados", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Recarregar", color = HomeGold, fontSize = 11.sp) }
        }
    }
    val rails: @Composable RowScope.() -> Unit = {
    listOf(Triple("Filmes • Novidades", movies, 2), Triple("Séries • Novidades", series, 3)).forEach { (title, entries, tab) ->
        if (entries.isNotEmpty()) {
            Column(if (landscape) Modifier.weight(1f).height(railHeight) else Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = if (landscape) 14.sp else 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onBrowse(tab) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Ver todos  ›", color = HomeGold, fontSize = 12.sp) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("home-rail-$tab")) {
                items(entries.take(24), key = { it.id }) { entry ->
                    Column(Modifier.width(if (landscape) 128.dp else 118.dp).clickable(enabled = !busy) { onOpen(entry) }) {
                        AsyncImage(model = entry.logoUrl, contentDescription = homeTitle(entry), contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(if (landscape) (railHeight - 54.dp).coerceAtLeast(1.dp) else 166.dp)
                                .clip(RoundedCornerShape(9.dp)).background(Color(0xFF202020)))
                        Text(homeTitle(entry), color = Color(0xFFDEDEDE), fontSize = 11.sp, lineHeight = 15.sp,
                            maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (landscape) 2.dp else 6.dp))
                    }
                }
            }
        }
    }
    }
    }
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), content = rails)
    } else {
        Row(Modifier.fillMaxWidth()) { Column { rails() } }
    }
    }
    }
}
