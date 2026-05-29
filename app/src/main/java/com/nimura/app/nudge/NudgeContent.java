package com.nimura.app.nudge;

import com.nimura.app.scoring.FatigueLevel;

import java.util.Random;

public class NudgeContent {

    private static final Random random = new Random();

    private static final String[] MILD_TITLES = {
            "ちょっと休憩しませんか？",
            "目を休めましょう"
    };

    private static final String[] MILD_MESSAGES = {
            "少し目を休めましょう。遠くを見てみてください。",
            "まばたきが減っています。意識的にまばたきしてみましょう。",
            "姿勢を正して、深呼吸してみましょう。"
    };

    private static final String[] MODERATE_TITLES = {
            "疲れが出てきています",
            "休憩のおすすめ"
    };

    private static final String[] MODERATE_MESSAGES = {
            "20-20-20ルール: 20秒間、6m先を見ましょう。",
            "軽いストレッチをしませんか？首と肩を回してみましょう。",
            "目の疲れが出ています。画面から目を離して、遠くを見てみましょう。"
    };

    private static final String[] SEVERE_TITLES = {
            "休憩が必要です！",
            "しっかり休みましょう"
    };

    private static final String[] SEVERE_MESSAGES = {
            "かなり疲れが溜まっています。5分間の休憩を取りましょう。",
            "目と体に大きな負担がかかっています。スマホを置いて休みましょう。",
            "長時間の使用で疲労が高まっています。立ち上がってストレッチしましょう。"
    };

    public static String getTitle(FatigueLevel level) {
        switch (level) {
            case MILD: return pick(MILD_TITLES);
            case MODERATE: return pick(MODERATE_TITLES);
            case SEVERE: return pick(SEVERE_TITLES);
            default: return "";
        }
    }

    public static String getMessage(FatigueLevel level) {
        switch (level) {
            case MILD: return pick(MILD_MESSAGES);
            case MODERATE: return pick(MODERATE_MESSAGES);
            case SEVERE: return pick(SEVERE_MESSAGES);
            default: return "";
        }
    }

    private static String pick(String[] array) {
        return array[random.nextInt(array.length)];
    }
}
