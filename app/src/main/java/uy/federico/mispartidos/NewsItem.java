package uy.federico.mispartidos;

import org.json.JSONObject;

final class NewsItem {
    final String title;
    final String source;
    final String link;
    final String team;
    final long publishedAt;

    NewsItem(String title,String source,String link,String team,long publishedAt){
        this.title=title==null?"":title.trim();
        this.source=source==null?"":source.trim();
        this.link=link==null?"":link.trim();
        this.team=team==null?"":team.trim();
        this.publishedAt=publishedAt;
    }

    JSONObject toJson() throws Exception {
        return new JSONObject().put("title",title).put("source",source).put("link",link).put("team",team).put("publishedAt",publishedAt);
    }

    static NewsItem fromJson(JSONObject value){
        return new NewsItem(value.optString("title"),value.optString("source"),value.optString("link"),value.optString("team"),value.optLong("publishedAt"));
    }
}
