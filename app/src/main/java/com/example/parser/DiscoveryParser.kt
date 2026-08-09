package com.example.parser

import android.util.Log
import com.example.model.Category
import com.example.model.Show
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object DiscoveryParser {
    
    private const val TAG = "DiscoveryParser"
    private const val TARGET_URL = "https://discoveryfm.ru/"

    val fallbackCategories = listOf(
        Category(
            name = "Музыкальные программы",
            shows = listOf(
                Show(
                    title = "Deep Flight",
                    description = "Глубокое погружение в мир качественного Deep House. Гипнотические мелодии, плотные басы и обволакивающая атмосфера для ночных путешествий."
                ),
                Show(
                    title = "Lounge Space",
                    description = "Расслабляющие вибрации Lounge и Ambient. Идеальный саундтрек для отдыха, ментальной перезагрузки и неторопливого созерцания."
                ),
                Show(
                    title = "Progressive Wave",
                    description = "Энергетическая волна Progressive House и Melodic Techno. Движение вперед, вдохновение космической эстетикой и глубокий грув."
                ),
                Show(
                    title = "Chillout Zone",
                    description = "Медленные ритмы и глубокие звуковые ландшафты. Музыка, останавливающая время и дарящая абсолютную гармонию."
                )
            )
        ),
        Category(
            name = "Познавательные шоу",
            shows = listOf(
                Show(
                    title = "Тайны Нашей Планеты",
                    description = "Загадочные, труднодоступные и самые удивительные уголки Земли. Истории об аномальных зонах, скрытых пещерах и древних тайнах."
                ),
                Show(
                    title = "Космическая Одиссея",
                    description = "Невероятные факты о Вселенной, далеких галактиках, феномене черных дыр и последних достижениях в покорении космоса."
                ),
                Show(
                    title = "Великие Экспедиции",
                    description = "Хроники исторических географических открытий и рассказы о легендарных первооткрывателях, изменивших карту мира."
                )
            )
        ),
        Category(
            name = "Технологии и Будущее",
            shows = listOf(
                Show(
                    title = "Discovery News",
                    description = "Обзор главных научных открытий, революционных изобретений и технологических прорывов, меняющих облик человечества."
                ),
                Show(
                    title = "Горизонты Будущего",
                    description = "Прогнозы ведущих футурологов, концептуальный транспорт завтрашнего дня и захватывающие сценарии развития искусственного интеллекта."
                )
            )
        )
    )

    fun fetchAndParse(): List<Category> {
        try {
            Log.d(TAG, "Connecting to $TARGET_URL for parsing...")
            
            val doc: Document = Jsoup.connect(TARGET_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .timeout(12000)
                .followRedirects(true)
                .get()

            val parsedCategories = mutableListOf<Category>()

            // Resilient strategy 1: Look for sections or container items
            val programCards = doc.select(".program, .show, .card, .item, article, [class*=program], [class*=show]")
            
            if (programCards.isNotEmpty()) {
                val shows = mutableListOf<Show>()
                for (card in programCards) {
                    val titleEl = card.select("h1, h2, h3, h4, h5, h6, .title, .name, [class*=title], [class*=name]").firstOrNull()
                    val descEl = card.select("p, .desc, .description, .text, [class*=desc], [class*=text]").firstOrNull()
                    
                    val title = Show.cleanDisplayTitle(titleEl?.text() ?: "")
                    val desc = Show.cleanDisplayTitle(descEl?.text() ?: "")
                    
                    if (title.isNotEmpty() && desc.isNotEmpty()) {
                        shows.add(Show(title, desc))
                    }
                }
                
                if (shows.isNotEmpty()) {
                    parsedCategories.add(Category("Программы радиостанции", shows.distinctBy { it.title }))
                }
            }

            // Resilient strategy 2: Parse sections with headers
            val sections = doc.select("section, .section, [class*=section]")
            for (sec in sections) {
                val secHeaderEl = sec.select("h1, h2, h3, h4, .section-title, .title, [class*=title]").firstOrNull()
                val secHeader = Show.cleanDisplayTitle(secHeaderEl?.text() ?: "")
                
                if (secHeader.isNotEmpty()) {
                    val secShows = mutableListOf<Show>()
                    val items = sec.select(".item, .program, .show, .card, p")
                    for (item in items) {
                        val t = Show.cleanDisplayTitle(item.select("h3, h4, h5, h6, strong, b, .title, [class*=title]").firstOrNull()?.text() ?: "")
                        val d = Show.cleanDisplayTitle(item.select(".desc, .text, p, [class*=desc], [class*=text]").firstOrNull()?.text() ?: "")
                        if (t.isNotEmpty() && d.isNotEmpty() && t != secHeader) {
                            secShows.add(Show(t, d))
                        }
                    }
                    if (secShows.isNotEmpty()) {
                        parsedCategories.add(Category(secHeader, secShows.distinctBy { it.title }))
                    }
                }
            }

            // Resilient strategy 3: Grab any elements that look like structured pairs
            if (parsedCategories.isEmpty()) {
                val boldTexts = doc.select("strong, b, h4, h5")
                val shows = mutableListOf<Show>()
                for (bold in boldTexts) {
                    val title = Show.cleanDisplayTitle(bold.text())
                    // Look for an immediately following text sibling or next paragraph
                    val nextSibling = bold.nextElementSibling()
                    val desc = Show.cleanDisplayTitle(nextSibling?.text() ?: "")
                    
                    if (title.length in 3..60 && desc.length in 10..400) {
                        shows.add(Show(title, desc))
                    }
                }
                if (shows.isNotEmpty()) {
                    parsedCategories.add(Category("Эфирные программы", shows.distinctBy { it.title }))
                }
            }

            // Filter out empty categories and remove exact duplicates
            val finalCategories = parsedCategories
                .filter { it.shows.isNotEmpty() }
                .distinctBy { it.name }

            if (finalCategories.isNotEmpty()) {
                Log.d(TAG, "Successfully parsed ${finalCategories.size} categories with Jsoup!")
                return finalCategories
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Jsoup parsing of $TARGET_URL", e)
        }

        Log.w(TAG, "Parsing returned empty result or failed. Fallback will be used.")
        return emptyList()
    }
}
