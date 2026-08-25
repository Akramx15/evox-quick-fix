# EvoX Quick Fix

[العربية](#العربية) · [English](#english) · [صفحة المشروع / Project page](https://akramx15.github.io/evox-quick-fix/) · [Releases](https://github.com/Akramx15/evox-quick-fix/releases)

> **Root utility — read the risks before using it.** Installing or updating the APK never disables a package or edits a system setting. If Vector is already configured, the exact-build Quick Search hooks load when Quick Search next starts.

## العربية

EvoX Quick Fix أداة مفتوحة المصدر لمعالجة أربع مشكلات محددة في رومات Android 16 المخصصة على عائلة Galaxy S23، وإدارة دبلوت اختياري قابل للاسترجاع:

- شفافية خلفية Recent Apps في الوضعين الداكن والفاتح.
- منع زر Back من إخراج شاشة Home في نسخة Quick Search المجربة.
- إصلاح آمن لفتح مجلدات التخزين الخارجي في نسخة Quick Search المطابقة، ومنها مجلد نتائج APK: يوجّهها إلى DocumentsUI بعد إزالة URI grant غير الصالح، ولا يثبت APK بصمت ولا يغيّر تطبيق الفتح الافتراضي.
- تعريف ميزة Google Circle to Search المفقودة بصورة systemless.
- ملف دبلوت ثابت من 104 حزم، باختيار يدوي وLedger ملكية واسترجاع دقيق.

### للمبتدئ: ما الذي تحتاجه؟

- **Root / KernelSU:** يمنح الأداة صلاحية تنفيذ تغييرات النظام. لا تدعم النسخة Magisk.
- **Magic Mount-rs:** تستخدمه الشفافية، ويحتاجه Circle فقط إذا كانت ميزة النظام مفقودة؛ لا تُثبت وحدة Circle زائدة إذا كانت الميزة أصلية في الروم.
- **Launcher3 / Quickstep:** يوفر Recents وتكامل Circle.
- **Quick Search:** تطبيق Home المستخدم في البيئة المجربة؛ مطلوب فقط لإصلاحي Quick Search المقفلين على الإصدار والبصمة المطابقين.
- **Vector:** يحمّل Hookي Back Guard وفتح مجلد نتائج APK داخل عملية Quick Search فقط؛ غير مطلوب لـCircle أو الدبلوت.
- **Google app:** مزود Circle to Search، وهو محمي دائمًا ولا يظهر كهدف دبلوت.

### التوافق

| الجهاز | الحالة |
|---|---|
| SM-S918B، Android 16، المستخدم 0، KernelSU | مجرب بالكامل |
| SM-S911* وSM-S916*، Android 16، المستخدم 0، KernelSU | تجريبي |
| S23 FE / SM-S711* | غير مدعوم |
| Magisk، ملفات العمل، المستخدمون الإضافيون | غير مدعوم |

تبقى الشفافية وإصلاحات Quick Search مقفلة على بيئة SM-S918B ونسخة Quick Search المطابقة التي اختُبرت فعليًا. يصلح Hook المجلد محاولات Quick Search لفتح مجلدات التخزين الخارجي المحتوية، ومنها نتائج APK، ويوجّهها صراحة إلى DocumentsUI بعد إزالة URI grant غير الصالح؛ لا يغيّر أي Handler أو تطبيق افتراضي. يعمل Circle بصورة مستقلة على أجهزة S23 المتوافقة عند توفر Launcher3 ومزود Google وخدمة contextual_search وKernelSU؛ ويُشترط Magic Mount-rs عند الحاجة إلى إضافة تعريف الميزة المفقود.

### التثبيت

1. احتفظ بنسخة احتياطية حقيقية وبوصول ADB أو Recovery.
2. حمّل APK من [صفحة Releases](https://github.com/Akramx15/evox-quick-fix/releases).
3. قارن SHA-256 قبل التثبيت.
4. ثبّت APK وافتحه واقرأ شاشة البداية وHelp.
5. نفّذ كل مجموعة إصلاح بصورة مستقلة؛ إصلاحا Quick Search يشتركان في زر Vector واحد. هذا الزر و«الإصلاحات الأربعة» يعيدان تشغيل Quick Search وينقلانك إلى Home عمدًا؛ هذا ليس crash.
6. أعد فتح الأداة لرؤية نتيجة العملية المحفوظة وطلب Restart.

### Debloat Profiles

- القائمة ثابتة: **104 Package IDs**، وبصمتها عند ترتيب الأسماء وربطها بـLF دون newline أخير:

  **bb24e159ce264a4fc9b823051b549206cceb73bef10e366c51046d2aea46488f**

- كل عنصر يبدأ على **Keep**. لا يوجد Disable All.
- التنفيذ الوحيد هو pm disable-user --user 0؛ لا uninstall ولا clear.
- الحزم العادية: خمس حزم كحد أقصى في الدفعة.
- الحزم عالية الخطورة: حزمة واحدة، مع كتابة Package ID حرفيًا.
- Google app وGMS وGSF وPlay Store وQuick Search وLauncher3 وSystemUI وShell والتطبيق نفسه ومزود Contextual Search وحاملو الأدوار الحالية محميون.
- الحزم المعطلة قبل استعمال الأداة تظهر Already disabled / External ولا تملك الأداة حق تفعيلها.
- بعد الدفعة يُفحص HOME وSystemUI ومزود Google وCircle. فشل الفحص يسترجع الدفعة عكسيًا.

### الاسترجاع

- زر **استرجاع وضع الروم** يخص الشفافية وHookي Quick Search وCircle فقط.
- زر **استرجاع تغييرات الدبلوت** يعيد فقط الحزم التي عطلتها الأداة، إلى حالة PackageManager الأصلية الدقيقة 0..4.
- الاسترجاع الطارئ للدبلوت:

  ~~~sh
  adb shell su -c 'sh /data/adb/evox-quick-fix/debloat-restore.sh'
  ~~~

- لتعطيل وحدتي الإصلاح عند تعذر الإقلاع:

  ~~~sh
  adb shell su -c 'touch /data/adb/modules/evox_contextual_search_fix/disable; touch /data/adb/modules/evox_overview_transparency/disable; reboot'
  ~~~

### التحقق والخصوصية

- APK لا يطلب صلاحية INTERNET.
- لا Analytics ولا WebView ولا Terminal ولا فتح تلقائي للروابط.
- CI يشغّل الاختبارات وlint ويبني APK غير موقع فقط.
- مفتاح توقيع الإصدار لا يدخل GitHub ولا GitHub Secrets.

v1.1.0-beta.1 APK SHA-256:

**41371910bafd15d5954afc21be223991b650e6fb3b06c010161e57e1e966acd1**

v1.0.1 APK SHA-256:

**f53819d94114e634537a510806f52cc158f88f2d5f2badc764b248a2dd841172**

## English

EvoX Quick Fix is an open-source root utility for four narrow Android 16 custom-ROM issues on the Galaxy S23 family, plus an optional recoverable debloat profile:

- Transparent Recent Apps scrim in dark and light modes.
- A narrowly scoped Back Guard for the verified Quick Search Home build.
- A safe external-storage folder fix for the exact Quick Search build, including APK results: containing-folder intents are routed to DocumentsUI after the invalid URI grant is removed, with no silent APK installation and no default-handler change.
- A systemless declaration for the missing Google Circle to Search feature.
- A fixed 104-package debloat profile with explicit selection, ownership ledger and exact rollback.

### Beginner requirements

- **Root / KernelSU:** authorizes system changes. Magisk is not supported.
- **Magic Mount-rs:** is used by transparency and is required by Circle only when the system feature is missing; no redundant Circle module is installed when the ROM already declares it.
- **Launcher3 / Quickstep:** supplies Recents and Circle integration.
- **Quick Search:** the Home app in the verified setup; only the exact-version/hash Quick Search hooks require it.
- **Vector:** loads the Back Guard and APK-result folder hooks only inside Quick Search; Circle and Debloat do not require it.
- **Google app:** the Circle provider. It is hard-protected and never offered as a debloat target.

### Compatibility

| Device | Status |
|---|---|
| SM-S918B, Android 16, user 0, KernelSU | Fully tested |
| SM-S911* and SM-S916*, Android 16, user 0, KernelSU | Experimental |
| S23 FE / SM-S711* | Unsupported |
| Magisk, work profiles, secondary users | Unsupported |

Transparency and the Quick Search hooks remain locked to the exact verified SM-S918B/Quick Search environment. The folder hook repairs Quick Search external-storage containing-folder intents, including APK results, by routing them to DocumentsUI after removing the invalid URI grant; it changes no registered handler or default app. Circle is independent and can run on a compatible S23 with Launcher3, the Google provider, contextual_search and KernelSU; Magic Mount-rs is additionally required only when the missing feature declaration must be supplied.

### Install

1. Keep a real backup and working ADB or Recovery access.
2. Download the APK from [Releases](https://github.com/Akramx15/evox-quick-fix/releases).
3. Verify SHA-256 before installing.
4. Install, open the app, and read onboarding and Help.
5. Apply each fix group independently; both Quick Search hooks share one Vector action. That action and “Apply the four fixes” intentionally restart Quick Search and switch to Home; that is not a crash.
6. Reopen the app for the persisted result and restart prompt.

### Debloat safety model

- Exactly **104 package IDs**, canonical set SHA-256:

  **bb24e159ce264a4fc9b823051b549206cceb73bef10e366c51046d2aea46488f**

- Every item defaults to **Keep**. There is no Disable All.
- The only mutation is pm disable-user --user 0; no uninstall and no data clearing.
- Normal batches are limited to five. A high-risk package must be alone and its exact Package ID must be typed.
- Google app, GMS, GSF, Play Store, Quick Search, Launcher3, SystemUI, Shell, this app, the actual contextual provider, and current role/IME holders are hard-protected.
- Previously disabled packages remain external and are never enabled by this app.
- HOME, SystemUI, Google provider and Circle invariants are checked before a batch is committed; failure triggers reverse rollback.

### Recovery

- **Restore ROM behavior** affects transparency, both Quick Search hooks, and Circle only.
- **Restore changes made by this app** restores only ledger-owned packages to their exact original PackageManager state 0..4.
- Emergency debloat restore:

  ~~~sh
  adb shell su -c 'sh /data/adb/evox-quick-fix/debloat-restore.sh'
  ~~~

- Core module emergency disable:

  ~~~sh
  adb shell su -c 'touch /data/adb/modules/evox_contextual_search_fix/disable; touch /data/adb/modules/evox_overview_transparency/disable; reboot'
  ~~~

### Privacy and verification

- No INTERNET permission, analytics, WebView, terminal, or automatic link opening.
- CI runs tests and lint and produces an unsigned APK only.
- The release signing key stays local and is not stored in GitHub or GitHub Secrets.

v1.1.0-beta.1 APK SHA-256:

**41371910bafd15d5954afc21be223991b650e6fb3b06c010161e57e1e966acd1**

v1.0.1 APK SHA-256:

**f53819d94114e634537a510806f52cc158f88f2d5f2badc764b248a2dd841172**

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Licensed under [Apache-2.0](LICENSE).
