package com.maxdemarzi.models;

import lombok.Data;

@Data
public class Post {
    private String status;
    private String name;
    private String username;
    private Long time;
    private Integer likes;
    private Integer reposts;
    private String reposter_name;
    private String reposter_username;

}
