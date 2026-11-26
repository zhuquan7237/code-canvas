package com.nous.codecanvas.ui;

import android.app.Activity;
import android.app.Dialog;

import com.nous.codecanvas.R;
import com.nous.codecanvas.util.AiCodeExtractor;
import java.util.List;

final class CodeBlockSheet {
    interface Choice { void use(String text, String language); }

    static Dialog show(Activity activity, List<AiCodeExtractor.CodeBlock> blocks,
                       String original, Choice callback) {
        int count = Math.min(AiCodeExtractor.MAX_BLOCKS, blocks.size());
        CanvasBottomSheet.Action[] actions = new CanvasBottomSheet.Action[count + 1];
        actions[0] = new CanvasBottomSheet.Action(0, R.drawable.ic_copy, "保留完整原文",
                "保留说明、代码与围栏", false);
        for (int i = 0; i < count; i++) {
            AiCodeExtractor.CodeBlock b = blocks.get(i);
            String name = "代码块 " + (i + 1) + (b.language.isEmpty() ? "" : " · " + b.language);
            String snippet = b.code.substring(0, Math.min(42, b.code.length()))
                    .replace('\n', ' ').replace('\r', ' ').trim();
            if (b.code.length() > 42) snippet += "…";
            actions[i + 1] = new CanvasBottomSheet.Action(i + 1, R.drawable.ic_paste, name, snippet, false);
        }
        String title = count == 1 ? "代码块与原文" : "选择要打开的代码";
        String note = blocks.size() > count ? "只列出前 " + count + " 个代码块，完整原文不受限制"
                : (count == 1 ? "可以只选代码，或保留说明" : "找到 " + count + " 个独立代码块");
        return CanvasBottomSheet.show(activity, title, note, actions, id -> {
            if (id == 0) callback.use(original, "plaintext");
            else callback.use(blocks.get(id - 1).code, blocks.get(id - 1).language);
        });
    }
}
