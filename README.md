# ✦ Mutu PDF Converter — Setup Guide

## 📦 What's included
```
MutuPDFConverter/
├── app/src/main/
│   ├── java/com/mutu/pdfconverter/
│   │   ├── MainActivity.java      ← UI + file handling
│   │   ├── WebViewRenderer.java   ← CSS/JS font engine
│   │   └── FontManager.java       ← Asset + custom font manager
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/colors.xml
│   │   ├── values/strings.xml
│   │   ├── values/themes.xml
│   │   └── drawable/ (5 shape files)
│   ├── assets/fonts/              ← PUT YOUR FONT FILES HERE
│   └── AndroidManifest.xml
├── app/build.gradle
├── build.gradle
└── settings.gradle
```

---

## 🔤 Step 1: Font Files রাখো

`assets/fonts/` ফোল্ডারে এই TTF গুলো রাখো:

| Font Name | File Name | Download |
|---|---|---|
| SolaimanLipi | SolaimanLipi.ttf | [Bangla Fonts](https://www.omicronlab.com) |
| Kalpurush | Kalpurush.ttf | [Kalpurush](https://ekushey.org) |
| Hind Siliguri | HindSiliguri-Regular.ttf | [Google Fonts](https://fonts.google.com/specimen/Hind+Siliguri) |
| Amiri | Amiri-Regular.ttf | [Google Fonts](https://fonts.google.com/specimen/Amiri) |
| Noto Naskh Arabic | NotoNaskhArabic-Regular.ttf | [Google Fonts](https://fonts.google.com/noto/specimen/Noto+Naskh+Arabic) |

> যদি কোনো font না থাকে, সেই script-এর জন্য system font use হবে।

---

## 🛠️ Step 2: Android Studio তে Open করো

1. Android Studio open করো
2. **File → Open** → `MutuPDFConverter` folder select করো
3. Gradle sync হওয়া পর্যন্ত অপেক্ষা করো
4. Phone connect করো (USB Debugging on)
5. ▶️ Run করো

---

## 📱 Step 3: App Use করো

1. **Upload HTML** বাটনে ক্লিক করো
2. Google Docs থেকে export করা HTML ফাইল pick করো
3. **বাংলা / Arabic / English** font dropdown থেকে পছন্দের font বেছে নাও
4. Preview এ দেখো ঠিক আছে কিনা
5. **Convert to PDF** চাপো → Android Print dialog আসবে
6. **Save as PDF** select করো

---

## 🔥 Google Docs থেকে HTML Export করবে কীভাবে?

**Google Docs → File → Download → Web Page (.html, zipped)**

ZIP extract করে `index.html` file টা app এ upload করো।

---

## 🚀 Future Features (পরে add করা যাবে)

- [ ] DOCX → HTML auto-convert (mammoth.js library)
- [ ] Online conversion API (CloudConvert)
- [ ] Per-paragraph font override
- [ ] Font size control
- [ ] Margin settings
- [ ] Multiple page sizes (A4, Letter, Legal)
