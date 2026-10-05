# Barkodu

Kargo barkodlarını kamera veya barkod okuyucu ile toplar, kargo firmasına göre listeler ve Excel/TXT olarak dışa aktarır.

- `KargoAndroid/`: Android 6+ için CameraX, çevrimdışı ML Kit ve Miuix arayüzü.
- `BarkodListe/`: tarayıcı arayüzü; kamera için HTTPS gerekir.

## v0.1-beta

İlk GitHub beta yayını, mevcut Android 1.2 uygulamasını içerir. APK iç sürümü ve paket adı güncelleme uyumluluğu için korunmuştur.

Releases sayfasındaki `KargoBarkod-1.2-Telefon.apk` telefonlar (ARM) içindir. `KargoBarkod-1.2-Miuix.apk` diğer mimarileri de içerir.

Tekrar okuma koruması, kalıcı uyarı ayarı, açık/koyu tema, yatay/dikey görünüm, geri alma ve Excel/TXT paylaşımı bulunur. Gerçek telefon kamera testi henüz yapılmamıştır.

## Derleme

Android Studio ile `KargoAndroid` klasörünü açın; JDK 17+ ve Android SDK 36 gerekir. Gradle sürümü 9.1.0'dır. `gradle testDebugUnitTest assembleRelease` komutuyla test ve APK oluşturabilirsiniz. Telefon APK'sı için `-PphoneApk` ekleyin.

Yerel SDK yolları, derleme önbellekleri ve imza anahtarı Git'e dahil değildir. Yerel anahtar yoksa derleme debug anahtarı kullanır; yayımlanan APK'ya güncelleme için aynı imza gerekir.

Miuix lisansı `KargoAndroid/app/src/main/assets/miuix-license.txt` dosyasındadır.
