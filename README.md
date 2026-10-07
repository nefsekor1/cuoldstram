# 🇹🇷 FSMNK Özel Cloudstream Türkçe Deposu

Bu depo, **fsmnk** için özel olarak yapılandırılmış Türkçe film, dizi, anime ve canlı televizyon (IPTV) eklenti havuzudur.

---

## 🔑 Kısa Kod: `fsmnk`

Cloudstream uygulamasında uzun URL'ler yazmak yerine **`fsmnk`** kısa kodunu kullanabilirsiniz!

### Cloudstream Kısa Kod Sistemi Nasıl Çalışır?
Cloudstream uygulamasında "Depo Ekle" alanına bir kelime yazdığınızda uygulama bunu otomatik olarak `https://cutt.ly/<KOD>` servisi üzerinden çözümler:
1. Deponuzu GitHub'a yükledikten sonra `repo.json` dosyanızın Raw linkini alın.
2. [cutt.ly](https://cutt.ly) sitesinden ücretsiz olarak bu linki kısaltın ve takma ad (custom alias) olarak **`fsmnk`** belirleyin (`cutt.ly/fsmnk`).
3. Artık Cloudstream'e sadece **`fsmnk`** yazarak kendi özel deponuzu anında yükleyebilirsiniz!

| Sağlayıcı | Kategori | Dil | Açıklama |
| :--- | :--- | :--- | :--- |
| **HDFilmCehennemi** | 🎬 Film & Dizi | TR | Türkiye'nin en popüler film ve dizi platformu |
| **FilmModu** | 🎬 Film | TR | HD kalitede güncel yabancı ve yerli filmler |
| **FilmMakinesi** | 🎬 Film | TR | Türkçe dublaj ve altyazılı film arşivi |
| **SetFilmIzle** | 🎬 Film & Dizi | TR | Donmasız 1080p full HD film ve diziler |
| **Dizilla** | 📺 Yabancı Dizi | TR | Güncel ve popüler yabancı dizi bölümleri |
| **DiziBox** | 📺 Yabancı Dizi | TR | Zengin yabancı dizi arşivi |
| **SezonlukDizi** | 📺 Yabancı Dizi | TR | Sezonluk ve güncel dizi arşivi |
| **TurkAnime** | 🍙 Anime | TR | Türkiye'nin lider online anime izleme platformu |
| **AnimeciX** | 🍙 Anime | TR | Geniş arşivli anime ve anime filmleri |
| **CanliTV** | 📡 Canlı TV | TR | Ulusal TV kanalları ve IPTV akışları (M3U) |
| **InatBox** | 📡 TV & Spor & Film | TR | Canlı yayınlar, maçlar ve film/dizi kanalları |
| **RecTV** | 📡 TV & Film | TR | Canlı TV yayınları ve film akışları |
| **Temel** | 🧩 Şablon | TR | Yeni eklenti yazmak için Türkçe açıklamalı temel şablon |

---

## 🚀 Cloudstream Uygulamasına Nasıl Eklenir?

### Kendi Deponuzu Eklemek İçin:
1. Bu projeyi kendi GitHub hesabınıza yükleyin.
2. Cloudstream uygulamasını açın.
3. **Ayarlar (Dişli simgesi) ⚙️ > Eklentiler (Extensions)** yolunu izleyin.
4. **Depo Ekle (Add Repository)** butonuna dokunun.
5. Bilgileri girin:
   - **Depo Adı:** `Cloudstream TR`
   - **Depo URL:** `https://raw.githubusercontent.com/<GITHUB_KULLANICI_ADINIZ>/<DEPO_ADINIZ>/master/repo.json`
6. **Ekle / İndir** butonuna basarak onaylayın.
7. Eklentiler listesinden dilediğiniz sağlayıcıyı seçip **Yükle (Install)** butonuna dokunun.

---

## ⚡ Mevcut Popüler Türkçe Topluluk Depoları (Hızlı Kurulum)

Kendi deponuzu GitHub'a yüklemeden önce doğrudan Cloudstream içine ekleyip hemen kullanabileceğiniz popüler Türkçe depolar:

| Depo Adı | Kısa Kod (Shortcode) | Doğrudan URL |
| :--- | :--- | :--- |
| **Kraptor** *(En Kapsamlı)* | `kraptorcs` | `https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/repo.json` |
| **Lawliet** | `lawlietrepo` | `https://raw.githubusercontent.com/Lawliet94/cs-lawliet/builds/repo.json` |
| **Kekik** | `KekikAkademi` | `https://raw.githubusercontent.com/maarrem/cs-Kekik/builds/repo.json` |

> **İpucu:** Cloudstream'de "Depo Ekle" ekranında URL yerine doğrudan **Kısa Kod** (örneğin `kraptorcs`) yazarak da ekleyebilirsiniz.

---

## 🛠️ Depoyu GitHub'da Yayınlama ve Otomatik Derleme

Bu depoyu kendi GitHub hesabınızda barındırıp otomatik `.cs3` eklenti paketleri oluşturmak için:

### 1. GitHub Deposu Oluşturun ve Kodu Gönderin
```bash
git init
git add .
git commit -m "İlk kurulum: Cloudstream TR Deposu"
git branch -M master
git remote add origin https://github.com/KULLANICI_ADINIZ/DEPONUZ.git
git push -u origin master
```

### 2. GitHub Actions İzinlerini Verin (Önemli!)
GitHub Actions'ın eklentileri derleyip `builds` dalına yükleyebilmesi için yazma izni gereklidir:
1. GitHub deponuzda **Settings (Ayarlar)** sekmesine gidin.
2. Sol menüden **Actions > General** seçeneğini açın.
3. **Workflow permissions** bölümünde **"Read and write permissions"** seçeneğini işaretleyip kaydedin.

### 3. Otomatik Derleme Nasıl Çalışır?
- Deponuza her `push` yaptığınızda `.github/workflows/build.yml` dosyası otomatik olarak tetiklenir.
- Ubuntu runner üzerinde JDK 17 ve Android SDK kurulur.
- `./gradlew make makePluginsJson` çalıştırılarak her eklenti `.cs3` formatında derlenir.
- Derlenen tüm `.cs3` dosyaları ve `plugins.json` otomatik olarak **`builds`** dalına (branch) gönderilir.
- `repo.json` dosyası da bu `builds` dalındaki `plugins.json`'ı referans gösterir.

---

## 💻 Geliştiriciler İçin: Yeni Sağlayıcı Ekleme

Yeni bir Türkçe video veya dizi sitesi için sağlayıcı eklemek çok kolaydır:

1. `Temel` klasörünü kopyalayıp yeni sitenin adıyla bir klasör oluşturun (örneğin `DiziPal`).
2. Klasör içindeki `build.gradle.kts` dosyasını açıp bilgileri güncelleyin:
   ```kotlin
   version = 1
   cloudstream {
       authors     = listOf("Adınız")
       language    = "tr"
       description = "DiziPal Dizi ve Film Sağlayıcısı"
       tvTypes     = listOf("TvSeries", "Movie")
       status      = 1
   }
   ```
3. `src/main/kotlin/...` altında `MainAPI` sınıfını doldurun:
   - `search(query: String)`: Arama sonuçlarını çeker.
   - `load(url: String)`: Bölüm, sezon, film detaylarını ve meta verileri çeker.
   - `loadLinks(data: String, ...)`: Video oynatıcı (M3U8 / MP4 / HLS / Extractor) bağlantılarını çözer.
4. Cihazınızda test etmek için ADB komutunu kullanabilirsiniz:
   ```powershell
   .\gradlew.bat DiziPal:deployWithAdb
   ```

---

## 📂 Dizin Yapısı

```
cuoldstram/
├── .github/
│   └── workflows/
│       └── build.yml               # Otomatik GitHub Actions derleyicisi
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar      # Gradle Wrapper binary
│       └── gradle-wrapper.properties
├── HDFilmCehennemi/                # Film & Dizi sağlayıcısı
├── Dizilla/                        # Yabancı dizi sağlayıcısı
├── DiziBox/                        # Yabancı dizi sağlayıcısı
├── SezonlukDizi/                   # Sezonluk dizi sağlayıcısı
├── FilmModu/                       # Film sağlayıcısı
├── FilmMakinesi/                   # Film sağlayıcısı
├── SetFilmIzle/                    # Film sağlayıcısı
├── TurkAnime/                      # Anime sağlayıcısı
├── AnimeciX/                       # Anime sağlayıcısı
├── CanliTV/                        # Canlı IPTV akışları sağlayıcısı
├── InatBox/                        # Canlı TV & Spor sağlayıcısı
├── RecTV/                          # Canlı TV & Film sağlayıcısı
├── Temel/                          # Şablon örnek eklenti
├── build.gradle.kts                # Ana Gradle yapılandırması
├── settings.gradle.kts             # Eklenti alt modül tanımları
├── gradle.properties               # JVM ve AndroidX ayarları
├── repo.json                       # Cloudstream depo manifesti
├── plugins.json                    # Eklenti katalog listesi
└── gradlew.bat / gradlew           # Gradle derleme betikleri
```

---

## ⚖️ Yasal Uyarı
Bu proje yalnızca eğitim, araştırma ve geliştirme amaçlıdır. Depo ve eklentiler herhangi bir video, film veya medya içeriğini kendi sunucularında barındırmaz; yalnızca internet üzerinde kamuya açık olarak yayınlanan üçüncü taraf web sitelerinin arayüzlerini ve oynatıcılarını ayrıştırmak için bir aracı olarak çalışır.
