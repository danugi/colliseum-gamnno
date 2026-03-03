# COLOSSEUM: SIEGE (Fabric server-side)

Версия таргета: **Fabric server-side 1.21.11** (в проекте сейчас 1.21.1 yarn, совместимость на уровне 1.21.x).

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

Реализовано через `fabric-permissions-api` (LuckPerms/Fabric permission adapters).

## Ограничения текущей итерации

- Вместо полноценного inventory GUI использован clickable чат + `/colosseum join`.
- Механика кузнечного mini-GUI для выбора эффекта Печати пока не добавлена (будет следующий шаг).
