package com.erol.glasskeys

import android.content.Context
import java.util.Locale

/**
 * Offline Turkish next-word and prefix predictor.
 * Learns only ordinary text input; callers must disable it for sensitive fields.
 * This is a compact local predictor, not a remote generative-AI service.
 */
internal class TurkishPredictionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("glass_keys_language", Context.MODE_PRIVATE)

    private val commonWords = """
        acaba açık adım ağrı akşam almak ama ancak anne ara araba artık arkadaş aslında at artık aşk
        bana bazen başla başlamak başka belki ben beni benim beraber beri beyaz bilgi bilmek birlikte bir biraz biri birkaç bizim biz bugün böyle büyük
        cevap çocuk çok çünkü da daha değil devam demek dene derdi ders dersin diye doğru dost dünya dün düşün düşünce düşünmek dört
        eğer elbette en erken ev evet fazla fakat fikir film fiyat gibi git gitmek göre güzel gün günaydın
        hangi hala hayat hak haklı haklısın halkı hakkında hangi hemen herkes her şey herkesin hiç hızlı hoş
        için iki ilk ile ilgili imkân insan insanı insanın internet iş işte iyi iyi ki
        kadar kadın kalmak kalp kamera kendi kesin kez ki kim kimse kitap kolay koltuk konu konuş konuşmak korku koy koymak kullan kullanmak
        lazım lakin lan lütfen maalesef madem mağaza makine mantık marka matematik mecbur merhaba mesele mesela mesaj mı mi misin mısın mutlaka
        nasıl nasılsın ne neden nerede nereye neredeyse niçin niye normal nokta olur olabilir olacak oldu oldukça olmak olmuyor olsun onu orada
        özel önce öğren öğrenmek örnek özgür
        para parça pardon park paylaş paylaşmak peki plan problem program proje rahat rağmen renk resim resmi sonuç sonra soru sor sorun söz söyle söylemek
        saat sabah sadece sakın sana sana göre sanırım sebep selam sen seni senin servis sevgi sevmek sence sessiz şekilde şimdi şehir şey sizin siz sizce
        tabii tabi takım tamam tamamen taraf tarafından tarih tavsiye tebrik telefon teşekkür tek tekrar temiz temel tercih ters teşekkürler
        tüm tür Türk Türkçe tut tutmak
        uygun uygulama ulaş ulaşmak uzun unut unutmak üst üzerine
        var varsa fazla ve veya veri ver vermek video yine yok yol yapmak yardım yanlış yani yarın yaş ya zaten yaz yazmak yeni yer yerine yeter
        güzel zor zaman zamanında zannetmek zengin zira
        merak ediyorum ne düşünüyorsun sence bence bence de olur mu olur mu acaba olabilir mi nasıl yapılır neden böyle teşekkür ederim iyi geceler günaydın
        nasılsın iyiyim teşekkürler sorun değil tamamdır olur tabii ki evet hayır belki sonra şimdi değil birazdan hemen geliyorum gidiyorum yapıyorum
    """.trimIndent().lowercase(Locale("tr", "TR"))
        .split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()

    fun suggestions(prefix: String, previousWord: String, limit: Int = 3): List<String> {
        val p = prefix.lowercase(Locale("tr", "TR"))
        val previous = previousWord.lowercase(Locale("tr", "TR"))
        val learningEnabled = prefs.getBoolean("personal_learning", true)
        val learnedWords = if (learningEnabled) prefs.getString("learned_words", "").orEmpty()
            .split('\u001F').filter { it.isNotBlank() } else emptyList()
        val personal = learnedWords.distinct()
        val contextual = contextWords(previous)
        val candidates = (contextual + personal + commonWords).distinct()
        return candidates.asSequence()
            .filter { it != p && it.isNotBlank() }
            .filter { candidate ->
                p.isEmpty() || candidate.startsWith(p) ||
                    (p.length >= 3 && TurkishPrefixMatcher.hasSingleEditPrefixMatch(candidate, p))
            }
            .sortedWith(
                compareByDescending<String> { score(it, p, previous, personal, contextual) }
                    .thenBy { it.length }
            )
            .take(limit)
            .toList()
    }

    fun learnTypedText(text: String, previousWord: String) {
        val clean = text.lowercase(Locale("tr", "TR"))
        if (!prefs.getBoolean("personal_learning", true)) return
        if (clean.isBlank() || clean.any { it.isDigit() } || clean.length > 40) return
        val existing = prefs.getString("learned_words", "").orEmpty()
            .split('\u001F').filter { it.isNotBlank() }.toMutableList()
        if (clean.matches(Regex("[\\p{L}\\p{M}'’-]+"))) {
            existing.remove(clean)
            existing.add(0, clean)
            prefs.edit().putString("learned_words", existing.distinct().take(1500).joinToString("\u001F")).apply()
        }
        if (previousWord.isNotBlank() && clean.matches(Regex("[\\p{L}\\p{M}'’-]+"))) {
            val key = previousWord.lowercase(Locale("tr", "TR")) + ">" + clean
            val old = prefs.getInt("pair:$key", 0)
            prefs.edit().putInt("pair:$key", (old + 1).coerceAtMost(1000)).apply()
        }
    }

    private fun contextWords(previous: String): List<String> {
        if (previous.isBlank()) return emptyList()
        val stored = prefs.all.entries.asSequence()
            .filter { it.key.startsWith("pair:$previous>") && (it.value as? Int ?: 0) > 0 }
            .sortedByDescending { it.value as Int }
            .map { it.key.substringAfter('>') }
            .take(12).toList()
        val rules = when (previous) {
            "ben" -> listOf("bir", "de", "değil", "seni", "bunu", "istiyorum", "geldim", "yapacağım")
            "sana" -> listOf("bir", "göre", "söylüyorum", "yardım", "anlatayım")
            "çok" -> listOf("iyi", "güzel", "kötü", "fazla", "önemli", "teşekkürler")
            "ne" -> listOf("oldu", "kadar", "zaman", "yapıyorsun", "demek", "var")
            "iyi" -> listOf("günler", "geceler", "misin", "oldu", "bir", "ki")
            "bir" -> listOf("şey", "daha", "tane", "kez", "süre", "sürü")
            "teşekkür" -> listOf("ederim", "ler")
            "nasıl" -> listOf("yapılır", "oldu", "gidiyor", "bir", "çalışır")
            "merhaba" -> listOf("nasılsın", "arkadaşlar")
            "ama" -> listOf("ben", "bu", "yine", "neden", "şimdi")
            else -> emptyList()
        }
        return (stored + rules).distinct()
    }

    private fun editDistanceForScore(a: String, b: String): Int {
        if (a == b) return 0
        return if (editDistanceAtMostOne(a, b)) 1 else 2
    }

    private fun score(word: String, prefix: String, previous: String, personal: List<String>, contextual: List<String>): Int {
        var value = 0
        if (prefix.isNotEmpty()) {
            value += if (word.startsWith(prefix)) 100 - (word.length - prefix.length).coerceAtLeast(0)
                else 45 - TurkishPrefixMatcher.distanceScore(word, prefix) * 10
        }
        if (word in contextual) value += 80
        if (word in personal) value += 35
        if (previous.isNotBlank()) {
            value += prefs.getInt("pair:" + previous + ">" + word, 0).coerceAtMost(100) * 3
        }
        value += when (word) {
            "bir", "ve", "bu", "da", "de", "için", "çok", "ben", "sen", "ne", "mi", "mı", "ama", "gibi" -> 15
            else -> 0
        }
        if (previous.isNotBlank() && word == previous) value -= 20
        return value
    }
}
