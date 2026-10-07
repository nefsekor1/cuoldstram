# 📖 FSMNK Özel Türkçe Depo ve Kısa Kod Kurulum Rehberi

Bu rehber, deponuzu **sadece size özel** hale getirmek ve Cloudstream uygulamasında **`fsmnk`** kısa kodu ile tek tıkla ekleyebilmeniz için gereken tüm adımları içerir.

---

## ⚡ 1. "fsmnk" Kısa Kodunun Çalışma Mantığı

Cloudstream uygulamasında **Ayarlar > Eklentiler > Depo Ekle** bölümünde URL yerine tek bir kelime (örneğin `fsmnk`) yazdığınızda, Cloudstream bunu arkada otomatik olarak **`https://cutt.ly/<KOD>`** (yani `https://cutt.ly/fsmnk`) olarak arar ve oradan gelen yönlendirmeyle `repo.json` dosyasını çeker.

Bu yüzden `fsmnk` kodunu tanımlamak için aşağıdaki adımları uygulamanız yeterlidir.

---

## 🔒 2. Deponuzu Size Özel (Kişisel) Olarak Yayına Alma

### Adım 1: GitHub Deposu Açın
1. [GitHub](https://github.com/) hesabınıza girip **New repository** butonuna tıklayın.
2. Depo adı: `fsmnk-cs` (veya dilediğiniz bir isim).
3. Cloudstream uygulamasının ek bir şifre girmeden dosyalara erişebilmesi için depoyu **Public** seçin (merak etmeyin; repo bağlantısını veya `fsmnk` kodunu sizden başka kimse bilmediği sürece depo tamamen size özel kalır, Cloudstream'in genel arama listesinde görünmez).

### Adım 2: Dosyaları Yükleyin
Bu klasörde PowerShell veya terminal açıp şu komutları girin:
```bash
git init
git add .
git commit -m "FSMNK Özel TR Deposu"
git branch -M master
git remote add origin https://github.com/<GITHUB_KULLANICI_ADINIZ>/fsmnk-cs.git
git push -u origin master
```

### Adım 3: GitHub Actions İznini Açın
1. GitHub'da deponuzun **Settings > Actions > General** sekmesine gidin.
2. Sayfanın en altındaki **Workflow permissions** kısmında **"Read and write permissions"** seçin ve kaydedin.
3. Böylece GitHub Actions eklentilerinizi otomatik olarak derleyip `builds` dalına gönderecektir.

---

## 🔗 3. "fsmnk" Kısa Kodunu Tanımlama (Cutt.ly Üzerinden)

1. [Cutt.ly](https://cutt.ly) sitesine gidin (ücretsiz bir hesap açabilirsiniz).
2. Kısaltılacak URL kısmına kendi `repo.json` dosyanızın Raw linkini yapıştırın:
   ```
   https://raw.githubusercontent.com/<GITHUB_KULLANICI_ADINIZ>/fsmnk-cs/master/repo.json
   ```
3. "Alias" (Özel Takma Ad / Custom name) kutucuğuna **`fsmnk`** yazın.
4. Oluştur butonuna basın. Böylece `cutt.ly/fsmnk` adresi sizin deponuza bağlanmış olur!

---

## 📱 4. Cloudstream Uygulamasında Kurulum

Artık tek yapmanız gereken:
1. Telefonunuzda veya Android TV'nizde **Cloudstream** uygulamasını açmak.
2. **Ayarlar ⚙️ > Eklentiler (Extensions) > Depo Ekle (Add Repository)** kısmına gitmek.
3. Depo alanına yalnızca **`fsmnk`** yazıp **Ekle** butonuna basmak!

Tüm Türkçe film, dizi, anime ve canlı TV eklentileri (HDFilmCehennemi, Dizilla, InatBox, TurkAnime vb.) anında karşınıza gelecektir.
