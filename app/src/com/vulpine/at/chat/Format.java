package com.vulpine.at.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.T;
import com.vulpine.at.ui.W;

public final class Format {
    static LinearLayout toolbar(Composer cp) {
        Context c = cp.s.a;
        LinearLayout box = W.col(c);
        box.setBackground(W.round(T.surface2, 12));
        box.setElevation(T.dpf(1));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = W.row(c);
        row.setPadding(T.dp(8), T.dp(6), T.dp(8), T.dp(6));
        String[][] items = {{"B", "**", "Bold"}, {"I", "*", "Italic"}, {"U", "__", "Underline"}, {"S", "~~", "Strikethrough"}, {"<>", "`", "Inline code"}, {"🔗", "link", "Link"}, {"H1", "# ", "Heading 1"}, {"H2", "## ", "Heading 2"}, {"H3", "### ", "Heading 3"}, {"•", "- ", "Bullet list"}, {"1.", "1. ", "Numbered list"}, {"✓", "- [ ] ", "Checklist"}, {"›", "> ", "Quote"}, {"{ }", "```", "Code block"}, {"••", "||", "Spoiler"}, {"—", "---", "Divider"}};
        for (String[] it : items) {
            TextView b = W.text(c, it[0], 12, 600, T.textSecondary);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(T.dp(28));
            b.setPadding(T.dp(7), 0, T.dp(7), 0);
            b.setContentDescription(it[2]);
            if (it[0].equals("I")) b.setTypeface(T.sb, Typeface.ITALIC);
            if (it[0].equals("U")) b.setPaintFlags(b.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            if (it[0].equals("S")) b.setPaintFlags(b.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            if (it[0].equals("<>") || it[0].equals("{ }")) b.setTypeface(Typeface.MONOSPACE);
            b.setBackground(W.ripple(W.round(0, 8), 8));
            b.setOnClickListener(v -> apply(cp, it[1]));
            row.addView(b, W.lpm(W.WC, T.dp(28), 0, 0, 4, 0));
        }
        hs.addView(row);
        box.addView(hs);
        return box;
    }

    static void apply(Composer cp, String mk) {
        EditText in = cp.input;
        Editable e = in.getText();
        int a = Math.max(0, in.getSelectionStart()), b = Math.max(0, in.getSelectionEnd());
        if (a > b) {
            int t = a;
            a = b;
            b = t;
        }
        if (mk.equals("link")) {
            String sel = e.subSequence(a, b).toString();
            Ov.Sheet s = new Ov.Sheet(in.getContext(), false);
            s.title("Link");
            W.Field f = new W.Field(in.getContext(), "https://example.com", null);
            if (sel.matches("^https?://\\S+$")) f.input.setText(sel);
            s.body.addView(f, W.lpm(W.MP, W.WC, 0, 0, 0, 16));
            final int fa = a, fb = b;
            s.body.addView(W.btn(in.getContext(), "OK", true, v -> {
                String u = f.value().trim();
                if (!u.matches("^https?://\\S{2,200}$")) {
                    Ov.toast("Invalid link", true);
                    return;
                }
                String label = sel.isEmpty() || sel.equals(u) ? u : sel;
                e.replace(fa, fb, "[" + label + "](" + u + ")");
                s.close();
            }), W.lp(W.MP, T.dp(36)));
            s.show();
            return;
        }
        if (mk.endsWith(" ") || mk.equals("---") || mk.equals("```")) {
            int ls = a;
            while (ls > 0 && e.charAt(ls - 1) != '\n') ls--;
            if (mk.equals("---")) e.insert(ls, "---\n");
            else if (mk.equals("```")) {
                e.insert(b, "\n```");
                e.insert(a, "```\n");
            } else e.insert(ls, mk);
            return;
        }
        String sel = e.subSequence(a, b).toString();
        if (sel.startsWith(mk) && sel.endsWith(mk) && sel.length() >= 2 * mk.length()) e.replace(a, b, sel.substring(mk.length(), sel.length() - mk.length()));
        else {
            e.replace(a, b, mk + sel + mk);
            if (sel.isEmpty()) in.setSelection(a + mk.length());
        }
    }
}
