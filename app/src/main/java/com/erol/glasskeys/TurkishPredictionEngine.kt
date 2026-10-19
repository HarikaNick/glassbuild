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
        aşağı açılmak açmak alışmak anlat anlatmak anlaşılmak aramak artmak atmak ay ayakkabı ayarlamak ayrılmak
        bakım bakmak banka başarmak başlangıç başvurmak beklemek benzer biçim bilgisayar bin binmek birinci bitirmek
        bırak bırakmak bul bulmak buluşmak burası bütün cevaplamak çalış çalışmak çalışan çalışma çalışma arkadaşım
        çanta çek çekmek çık çıkmak çıkış çiftçi ciddi cilt cihaz cümle cevaplar çevre çevirmek çeviri çorba
        daire dakika dikkat dinle dinlemek dinlenmek dinlenme doğa doğmak dokunmak doktor dolu dön dönmek döner
        düşmek düşünmek duy duymak duygu düzen düzenlemek düzeltmek eğlence eğlenmek eklemek ekip ekmek ekran
        elektrik elektronik emek emekli emin emniyet enerji engel engellemek ekipman eşya et etmek etki etmek
        evde evden evim evine evler evlilik eylül fabrika fayda faydalı farklı fark etmek farkında olmak
        fırsat fotoğraf fotoğrafçı futbol futbolcu galiba garanti gaz geç geçmek gelecek geliş gelişmek gelişim
        gelişmiş gelin gelmek geniş gereği gerekli gerekmek geri getirmek gez gezmek geçmiş geçirmek geçiş
        gidip girmek giriş gitmiş görmek göster göstermek görev görevli göz gözlük güç güçlenmek güçlük
        hafta haftalık haftaya haber haberler haberleşmek hazır hazırlamak hazırlık hatırlamak hava havalı
        hayatım hayvan hayırlı hesap hesaplamak hesaplama hediye hepsi heyecan heyecanlı his hissetmek hizmet
        hoşça kal hoşgeldin hukuk huzur ısınmak ışık ihtiyaç içmek içerik içeride içeriye ilerlemek iletişim
        imza inanç inanmak indirmek ince incelemek indirim insanlık insanlar insanlarla insanlara insanlardan
        inşallah iptal iptal etmek işçi işlemek işlemler işlemci işyeri işveren işaret işaretlemek istemek
        istasyon istifa istiyorum istiyorsun istiyor istiyoruz istedim istediğin istediğiniz İstanbul İzmir
        izlemek kaç kaçmak kaçıncı kadar kaldı kalem kalın kaldırmak kalmak kan kanun kapalı kapat kapatmak
        kapı kaplamak karar karar vermek karşı karşılaşmak karşılaştırmak kat katılmak katman katmak kaynak
        kaydet kaydetmek kayıp kaybolmak kaydetti kazan kazanmak kazanç kaza kelebek kendim kendine kendisi
        kesinlikle keşke keşfetmek keyif keyifli kıdem kıllı kimlik kiralık kiralamak kirli kişisel kişi kişiler
        kısım kıta kıyafet klasör kod kodlamak kol kolayca komik komisyon komple kontrol kontrol etmek kontrolcü
        korumak koşmak kova koymak kredi kritik kulak kullanılıyor kullanılıyor mu kullanıcı kullanıcının kullanım
        kur kurmak kuruluş kurul kurulmak kurs kurtarmak kurtulmak kusura bakma kuşku kuyruk kuvvetli
        lazım mı lider liderlik liste listelemek lokanta lüks makale mal malzeme mantıklı market masraf
        mektup memnun memnuniyet merkez merkezî mesai meslek meşgul meşgulüm meşgulsün metre miktar milyon
        mümkün müdür müzik müzisyen naber nakit nakliye nasılca nasip neyse net netice nişan numara
        oda odak odaklanmak ofis oğlan oğlum ok okula okuldan okulda okumak okuyor olmak onay onaylamak
        ondan sonra onlara onları onuncu onur operasyon operatör ortak ortaklık ortam otel otomasyon otomatik
        otur oturmak oyun oyuncu oynama oynamak ödeme ödemek ödev öyleyse öğret öğretmek öğretmen öğretmenim
        ömür önemli önemsemek öneri önermek örnekler özet özetlemek paket paketlemek paketleme para para birimi
        parmak parça parlamak paylaşım paylaşmak patlamak pay ödeme personel performans perşembe pişirmek planlamak
        platform polis politika popüler portre pozisyon pratik profesyonel proje projesi proje yönetimi psikoloji
        radyo rapor raporlama raporlamak randevu rastlamak rahatlamak reddetmek rehber reklam resmi olarak
        risk riskli robot rol rota ruh ruhsal ruhsat sabit sabitlemek sabretmek saç sağ sağlamak sağlık
        sahip sahip olmak saklamak sakıncalı sakin sakinleşmek salı sanal sanayi sanat sanatçı say sayfa
        saygı saygılı saymak sayısı sayısal sebze seç seçmek seçim seçeneği sefer sevgili sevinç sevinmek
        servisçi ses sesli sessizlik set seyahat seyretmek sıcak sıcaklık sık sıkışık sıkıntı simge simit
        sistem sistemli sistemler site sohbet soğuk soğutmak sokak son sonbahar sonradan sonuçlanmak sorabilmek
        soracak soran sordum sormak sorumlu sorumluluk sosyal sosyal medya sözlük söyleyeceğim söylemek
        spor sporcu standart staj stajyer strateji stres süper süre sürmek sürüm süs süzmek şaka şans
        şart şartlar şef şeker şehirler şifre şişe şişirmek şirket şirketler şirketin şubat tabak takip
        takipçi takip etmek takılmak taksit takvim tamir tamir etmek tanı tanım tanımak tanışmak tanıtım
        tanıtmak tartışmak tasarım tasarlamak taş taşımak taşınmak tavuk teknik teknoloji teklif teklif etmek
        telefoncu temelinde temizlemek tercih etmek terfi teşekkür etmek test test etmek tespit tespit etmek
        teslim teslimat teslim etmek tesis tesisat tezgah ticaret tarihî toplantı toplamak toplum toparlamak
        toplam toplamda trafik tren tuhaf tur turizm tutmak tuvalet türkiye tüyü tıklamak tıklama
        uç uçak uçmak ulaşım uluslararası unutma unutulmak uygulama uygulamak uzman uzmanlık uygunluk
        ulaşılabilir ücret ücretli ücretsiz üret üretim üretmek üretici ürün ürünler ürünün üretim
        üşümek üzgün üzülmek vade vaka vakit valiz vatandaş vazgeçmek vazgeçtim vergi vermek versiyon
        verimli veri tabanı ziyaret ziyaretçi ziyaret etmek zayıf zahmet zahmetli zaman zamanlar zam yapmak
        zamlı zarf zarar zararlı zayıflamak zevk zevkli zihin zihinsel zincir zorlanmak zorunlu zorunluluk
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
