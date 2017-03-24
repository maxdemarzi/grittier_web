package com.maxdemarzi.models;

import lombok.Data;

@Data
public class User {
    private String username;
    private String name;
    private String email;
    private String password;
    private String hash;
    private Integer likes;
    private Integer posts;
    private Integer followers;
    private Integer following;
}