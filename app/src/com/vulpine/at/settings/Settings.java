package com.vulpine.at.settings;

import com.vulpine.at.App;
import com.vulpine.at.MainActivity;
import com.vulpine.at.Prefs;
import com.vulpine.at.home.Home;
import com.vulpine.at.ui.Ov;
import com.vulpine.at.ui.Screen;

public final class Settings {
    public static final String[][] ROWS = {
            {"account", "Account", "person", "#3b82f6", "USER SETTINGS"},
            {"pro", "Pro Box", "workspace_premium", "#f59e0b", "USER SETTINGS"},
            {"appearance", "Appearance", "palette", "#a855f7", "APP SETTINGS"},
            {"notifications", "Notifications", "notifications", "#ef4444", "APP SETTINGS"},
            {"messages", "Messages", "chat", "#22c55e", "APP SETTINGS"},
            {"voice", "Voice & Video", "videocam", "#14b8a6", "APP SETTINGS"},
            {"privacy", "Privacy", "shield", "#6366f1", "SECURITY"},
            {"storage", "Storage", "set.storage", "#f97316", "SECURITY"},
            {"security", "Security & Keys", "key", "#64748b", "SECURITY"},
            {"bughunter", "Bug Hunter", "bug_report", "#84cc16", "OTHERS"},
            {"qr", "QR Login", "set.qr", "#0ea5e9", "LOGIN"}};

    public static void openPage(MainActivity a, String page) {
        if (a == null) return;
        if (page == null || page.isEmpty()) page = "account";
        switch (page) {
            case "pro":
                com.vulpine.at.pro.ProBox.open(a);
                return;
            case "qr":
                QrLogin.open(a);
                return;
            case "profile":
                if (Home.I != null) {
                    a.popTo(Home.I);
                    Home.I.select("profile", false);
                }
                return;
            case "logout":
                logoutConfirm();
                return;
        }
        Screen s = make(page);
        if (s != null) a.push(s);
    }

    static SetPage make(String page) {
        switch (page) {
            case "account": return new AccountPage();
            case "appearance": return new AppearancePage();
            case "notifications": return new NotifPage();
            case "messages": return new MessagesPage();
            case "voice": return new VoicePage();
            case "privacy": return new PrivacyPage();
            case "storage": return new StoragePage();
            case "security": return new SecurityPage();
            case "bughunter": return new BugHunterPage();
        }
        return null;
    }

    public static void logoutConfirm() {
        Ov.confirm("Log out?", "You'll need your token to log back in. Your recovery phrase is the only way to access your account on a new device.", "Log out", true, () -> App.logout(true));
    }

    public static void rerender(MainActivity a, String reopen) {
        if (a == null) return;
        Prefs.applyTheme();
        a.applyBars();
        Home h = new Home();
        a.setRoot(h);
        h.select("settings", false);
        if (reopen != null) {
            SetPage p = make(reopen);
            if (p != null) {
                a.push(p);
                p.root.animate().cancel();
                p.root.setTranslationX(0);
                ((Screen) h).root.animate().cancel();
                ((Screen) h).root.setTranslationX(0);
                ((Screen) h).root.setVisibility(android.view.View.INVISIBLE);
            }
        }
    }
}
