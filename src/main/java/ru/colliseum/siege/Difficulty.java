package ru.colliseum.siege;

public enum Difficulty {
    EASY(10, "Капитан Падали"),
    MEDIUM(30, "Командир Стрелков"),
    HARD(40, "Осадный Чемпион"),
    HARDCORE(40, "Палач Арены");

    private final int waves;
    private final String finalBossName;

    Difficulty(int waves, String finalBossName) {
        this.waves = waves;
        this.finalBossName = finalBossName;
    }

    public int waves() {
        return waves;
    }

    public String finalBossName() {
        return finalBossName;
    }
}
