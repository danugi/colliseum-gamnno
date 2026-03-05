# COLOSSEUM: SIEGE (Fabric client+server)

Версия таргета: **Fabric 1.21.11 (client + server)**.

Текущая версия мода: **0.3.1**.

Требуемая Java для сборки/рантайма: **Java 21**.

Можно тестить и на выделенном сервере, и в одиночном мире (integrated server).

## Что реализовано по плану

- PvE режим с волнами и финальным боссом.
- 4 сложности: Easy / Medium / Hard / Hardcore.
- 2 режима: Classic / Elite.
- Группа 1-5 игроков.
- Награды выдаются только за победу (убийство финального босса).
- При поражении наград нет вообще.
- КД всегда ставится (и при win, и при lose) по таблице из ТЗ.
- Выход игрока во время боя = поражение сессии.
- Мобы не горят, действует шанс baby-zombie (30%, Hard/Hardcore 40%).
- Масштабирование мобов по игрокам и бусты Hardcore.
- Elite режим: снапшот инвентаря -> аренный сет -> восстановление после боя даже при лузе.
- Админ-настройка арен и сохранение в `config/colosseum_arenas.json`.
- Базовая реализация предмета "Печать Колизея" в наградах (NBT marker), анти-стак на уровне выдачи (count по плану), PvE-only логика применения в бою.


## Что нового в 0.3

- Добавлен BossBar прогресса волн: показывает текущую волну (`Волна X/Y`) и заполняется по мере убийства мобов волны.
- Выход за радиус арены теперь не телепортирует в центр: игрока отталкивает обратно в арену.
- Граница арены стала визуально заметной: по кругу радиуса отображается красный пылевой "барьер" из частиц.
- При выходе за радиус игрок мгновенно возвращается внутрь границы (инста-коррекция позиции) и сразу получает отталкивание назад.

## Шансы наград

Награды выдаются **только за победу**. Часть наград гарантирована, часть — с шансом:

- **EASY**: 
  - Шанс `10%`: книга `Книга IV`.
- **MEDIUM**:
  - Шанс `40%`: +1 Ancient Debris.
  - Шанс `20%`: +1 Golden Apple.
  - Шанс `25%`: книга `Книга IV`.
- **HARD**:
  - Шанс `50%`: +1 Ancient Debris (дополнительно к гарантированному).
  - Шанс `35%`: книга `Mending`.
  - Шанс `30%`: книга `Unbreaking III`.
  - Шанс `25%`: книга `Efficiency V`.
  - Шанс `25%`: книга `Sharpness V`.
  - Шанс `20%`: +1 Golden Apple.
  - Шанс `25%`: +1 Totem of Undying.
- **HARDCORE**:
  - Шанс `50%`: +1 Totem of Undying.
  - Шанс `40%`: дополнительная книга `Mending`.
  - Шанс `35%`: книга `Sharpness V`.
  - Шанс `35%`: книга `Protection IV`.
  - Шанс `30%`: книга `Efficiency V`.
  - Шанс `25%`: книга `Unbreaking III`.
  - Шанс `25%`: +1 Golden Apple.

## Команды

### Игроки (`colosseum.use`)
- `/colosseum queue <arena> [difficulty] [mode]`
- `/colosseum join [arena]`

### Админ (`colosseum.admin`)
- `/colosseum edit <arena>`
- `/colosseum setcenter`
- `/colosseum setradius <r>`
- `/colosseum addspawn`
- `/colosseum setentry`
- `/colosseum setexit`
- `/colosseum setlobby`
- `/colosseum setmaxplayers <n>`
- `/colosseum save <name>`

Технические админ-команды тестирования:
- `/colosseum win`
- `/colosseum lose`

## Пермишены

- `colosseum.use`
- `colosseum.admin`

В текущей сборке используется упрощённая проверка внутри `ModPermissions` (без внешнего permission API).


## Сборка

Для корректной работы Gradle/Loom запускай сборку под **JDK 21** (на JDK 25 возможна ошибка `Unsupported class file major version 69`).

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
gradle classes
```

## Ограничения текущей итерации

- Вместо полноценного inventory GUI использован clickable чат + `/colosseum join`.
- Механика кузнечного mini-GUI для выбора эффекта Печати пока не добавлена (будет следующий шаг).


### Toolchain note
Используй локальную установленную **Java 21** (в IDE укажи Gradle JVM = JDK 21).
