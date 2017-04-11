package com.maxdemarzi.models;

import humanize.Humanize;
import lombok.Data;

import java.util.Date;

@Data
public class Post {
    private String status;
    private String name;
    private String username;
    private String hash;
    private Long time;
    private String human_time;
    private Integer likes;
    private Integer reposts;
    private String reposter_name;
    private String reposter_username;
    private boolean liked;
    private boolean reposted;

    public String humanTime() {
        return Humanize.naturalTime(new Date(time * 1000));
    }
}
