package com.maxdemarzi.models;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;

@Data
public class User {
    private String username;
    private String name;
    private String email;
    private String password;
    private String hash;
    private Long time;
    private Integer likes;
    private Integer posts;
    private Integer followers;
    private Integer following;
    private Boolean i_follow;
    private Boolean follows_me;
    private Integer followers_you_know_count;
    private ArrayList<HashMap<String, Object>> followers_you_know;
}