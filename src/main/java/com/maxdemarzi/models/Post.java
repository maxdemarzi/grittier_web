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

    public void setTime(Long time) {
        this.time = time;
        human_time = Humanize.naturalTime(new Date(time * 1000));
    }

}
