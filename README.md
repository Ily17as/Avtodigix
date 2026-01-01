# Avtodigix

Avto-scan app for car users.

## Требования
- **Android Studio** (Giraffe/2022.3 или новее)
- **JDK** 17 (подойдет встроенный JDK Android Studio)
- **Gradle** (используется Gradle wrapper из репозитория)
- **Поддержка MVP**: Android 10+ (API 29+) — соответствует `minSdk 29` в `app/build.gradle`.
- **Android SDK**:
  - Android SDK Platform 34+
  - Android SDK Build-Tools 34+
  - Android SDK Platform-Tools

## Стек
- **Kotlin**
- **AndroidX**
- **Room** для хранения снимков сканирования (последний скан и история).
- **View Binding** включен для layout’ов (см. `buildFeatures { viewBinding true }`).

## Сборка APK
Из корня репозитория:

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```

### Где искать APK
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

> Для release APK требуется настройка подписи. Обновите `app/build.gradle` (keystore) или используйте Android Studio → “Generate Signed Bundle / APK”.

## Запуск
### Установка на подключенное устройство/эмулятор
```bash
./gradlew installDebug
```

### Запуск из Android Studio
1. Откройте проект в Android Studio.
2. Дождитесь синхронизации Gradle.
3. Выберите устройство/эмулятор.
4. Нажмите **Run**.

## Документация
- [Поддерживаемые параметры и ограничения](docs/supported-parameters.md)
- [Протестированные адаптеры и автомобили](docs/tested-adapters.md)
