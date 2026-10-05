# Kargo Barkod Android uygulaması

Android 6 ve üzeri için yerel CameraX ve çevrimdışı ML Kit barkod okuyucu. Kamera ve liste aynı ekranda; dört kargo, Bilinmeyen sekmesi, tekrar sayısı, geri alma, Excel ve TXT paylaşımı bulunur.

Android Studio ile bu klasörü açın. SDK 36 gerekir. Kurulum APK dosyası üst klasörde KargoBarkod-1.0.apk olarak bulunur. Yerel kurulum için debug anahtarıyla imzalanmış release derlemesidir. GitHub beta yayını: https://github.com/omertrans678/Barkodu/releases/tag/v0.1-beta

Doğrulama: release derlemesi, APK imzası ve dört birim testi başarılı. Gerçek cihaz kamera ve ekran testi henüz yapılmamıştır.


## Sürüm 1.1 — Miuix

Miuix 0.8.8 ve Jetpack Compose ile açık/koyu tema, yuvarlak kartlar ve altta kargo/işlem düğmeleri. Dikeyde kamera üstte ve liste altta; yatayda yan yana. Barkod ve dışa aktarma kuralları korunur. Aynı paket adı ve imza ile 1.0 üzerine kurulabilir. Liste verisi aynı SharedPreferences kaydında korunur.

Kaynak: https://github.com/compose-miuix-ui/miuix (Apache 2.0). Lisans APK assets içinde bulunur.

Doğrulama: 4 model/Excel testi ve 2 Miuix ekran testi (dikey iş akışı, yatay koyu tema) geçti. Release derlemesi, APK imzası ve 16 KB hizalaması doğrulandı. 1.0 ile imza eşleşir. Gerçek telefon kamera testi yapılmadı.


## Sürüm 1.2 — Tekrar okuma koruması

Kamerada tutulan barkod tekrar eklenmez. Kısa okuma kayıpları kilidi açmaz; en az 500 ms boyunca tekrarlanan boş sonuçlardan sonra aynı kod ikinci sipariş olarak sayılabilir. Farklı kodlar beklemeden eklenir. Kamera kapatıp açılması ve ekran dönüşü aynı kodun kilidini açmaz.

Ayarlar > Tekrar okuma uyarısı varsayılan olarak açıktır, tercih kalıcıdır. Barkod tutulurken bir kez kısa bildirim gösterilir. Uyarıyı kapatmak tekrar korumasını kapatmaz.

13 test geçti: 4 model/Excel, 6 okuma kilidi ve 3 Miuix ekran testi. Güncelleme imzası ve APK hizalaması doğrulandı. Gerçek telefon kamera testi yapılmadı.

