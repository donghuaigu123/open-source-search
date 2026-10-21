package com.opensourcesearch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** GitHub 公共 API 封装：按星标从高到低搜索开源仓库。 */
public final class GitHubApi {

    private static final String BASE_URL = "https://api.github.com/search/repositories";

    /** 搜索结果：总数 + 仓库列表。 */
    public static final class SearchResult {
        public final int totalCount;
        public final List<Repo> items;

        SearchResult(int totalCount, List<Repo> items) {
            this.totalCount = totalCount;
            this.items = items;
        }
    }

    /** API 错误（携带中文可读信息）。 */
    public static final class ApiException extends Exception {
        public ApiException(String message) {
            super(message);
        }
    }

    private GitHubApi() {
    }

    /**
     * 搜索仓库，按星标从高到低排序。
     *
     * @param keyword  关键词
     * @param perPage  每页数量（不超过 30）
     */
    public static SearchResult search(String keyword, int perPage) throws IOException, ApiException {
        int size = Math.min(Math.max(perPage, 1), 30);
        String urlStr = BASE_URL
                + "?q=" + URLEncoder.encode(keyword, "UTF-8")
                + "&sort=stars&order=desc"
                + "&per_page=" + size
                + "&page=1";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "OpenSourceSearch-Android");
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(is);

            if (code == 403 || code == 429) {
                throw new ApiException("GitHub API 访问频率超限（匿名搜索约每分钟 10 次），请稍后再试");
            }
            if (code < 200 || code >= 300) {
                String msg = extractMessage(body);
                throw new ApiException("请求失败（" + code + "）" + (msg.isEmpty() ? "" : "：" + msg));
            }

            JSONObject root = new JSONObject(body);
            int total = root.optInt("total_count", 0);
            JSONArray items = root.optJSONArray("items");
            List<Repo> list = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    list.add(Repo.fromJson(items.getJSONObject(i)));
                }
            }
            return new SearchResult(total, list);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new ApiException("数据解析失败：" + e.getMessage());
        } finally {
            conn.disconnect();
        }
    }

    private static String extractMessage(String body) {
        try {
            JSONObject o = new JSONObject(body);
            return o.optString("message", "");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
