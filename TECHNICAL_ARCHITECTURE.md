# BitChat Mesh - الهندسة التقنية المفصلة

## 1. نظرة عامة على البنية

### المكونات الرئيسية

- **User Interface Layer**: MainActivity (Discovery) + ChatActivity (Messaging) + MessageAdapter
- **Business Logic Layer**: ConnectionManager (Networking & Routing) + CryptoUtils (AES-256) + Message (Data Model)
- **Communication Layer**: Google Nearby Connections API (Strategy.P2P_CLUSTER)
- **Hardware Layer**: Bluetooth LE / Classic + Wi-Fi Direct

## 2. Mesh Networking

يستخدم التطبيق استراتيجية **P2P_CLUSTER** من Google Nearby Connections:
- تسمح بطوبولوجيا M-to-N (كل جهاز يمكن أن يتصل بعدة أجهزة)
- تدعم Multi-Hop بشكل طبيعي عبر إعادة البث (Relay)

### آلية منع الحلقات (Loop Prevention)
- كل رسالة لها `id` فريد (UUID)
- يتم تخزين الرسائل المرئية في `seenMessages` Set
- عند استقبال رسالة سبق رؤيتها → يتم تجاهلها
- عند الاستقبال الجديد → يتم زيادة `hopCount` وإعادة البث لباقي العقد

## 3. التشفير (AES-256-CBC)

- المفتاح: مشتق من `SHARED_SECRET` عبر SHA-256
- الوضع: AES/CBC/PKCS5Padding
- IV عشوائي 16 بايت يُرفق مع كل رسالة
- النتيجة تُحوّل إلى Base64

**ملاحظة أمنية:** المفتاح المشترك ثابت حالياً. في الإنتاج يُفضّل تبادل مفاتيح عبر Diffie-Hellman أو QR Code.

## 4. تدفق الرسالة

1. المستخدم يكتب رسالة → `ChatActivity.sendMessage()`
2. إنشاء كائن `Message` + UUID
3. تحويل إلى JSON بواسطة Gson
4. تشفير بواسطة `CryptoUtils.encrypt()`
5. إرسال عبر `ConnectionManager.broadcastMessage()`
6. عند الاستقبال:
   - فك التشفير
   - التحقق من `seenMessages`
   - عرض في الواجهة
   - زيادة hop وإعادة البث (Relay)

## 5. الصلاحيات المطلوبة

- Bluetooth (قديمة + Android 12+)
- Location (Nearby يحتاجها)
- NEARBY_WIFI_DEVICES (Android 13+)

## 6. نقاط التحسين المستقبلية

- نقل ConnectionManager إلى Service
- تبادل مفاتيح ديناميكي
- دعم نقل ملفات
- Group chat
- تحسين استهلاك البطارية
