package com.opensourcesearch;

import java.util.ArrayList;
import java.util.List;

/** 内置浏览器多标签管理（进程内单例）。 */
public final class TabManager {

    private TabManager() {
    }

    /** 标签页信息。 */
    public static final class Tab {
        public final long id;
        public String url;
        public String title;

        Tab(long id, String url, String title) {
            this.id = id;
            this.url = url;
            this.title = title;
        }
    }

    private static final List<Tab> TABS = new ArrayList<>();
    private static long counter = 0;

    /** 查找与 url 相同的标签页，不存在则创建。 */
    public static synchronized Tab ensure(String url) {
        for (Tab t : TABS) {
            if (t.url != null && t.url.equals(url)) {
                return t;
            }
        }
        return create(url);
    }

    /** 强制创建新标签页。 */
    public static synchronized Tab create(String url) {
        Tab t = new Tab(++counter, url, "新标签页");
        TABS.add(t);
        return t;
    }

    public static synchronized void update(long id, String url, String title) {
        for (Tab t : TABS) {
            if (t.id == id) {
                t.url = url;
                if (title != null && !title.isEmpty()) {
                    t.title = title;
                }
                return;
            }
        }
    }

    public static synchronized void close(long id) {
        for (int i = 0; i < TABS.size(); i++) {
            if (TABS.get(i).id == id) {
                TABS.remove(i);
                return;
            }
        }
    }

    public static synchronized List<Tab> all() {
        return new ArrayList<>(TABS);
    }

    public static synchronized int count() {
        return TABS.size();
    }
}
