package com.maxdemarzi;

import com.maxdemarzi.models.Post;
import com.maxdemarzi.models.User;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

import java.util.List;

public interface GritterService {

    @GET("users/{username}")
    Call<User> getUser(@Path("username") String username);

    @GET("users/{username}/profile")
    Call<User> getProfile(@Path("username") String username);

    @POST("users")
    Call<User> createUser(@Body User user);

    @GET("users/{username}/followers")
    Call<List<User>> getFollowers(@Path("username") String username);

    @GET("users/{username}/following")
    Call<List<User>> getFollowing(@Path("username") String username);

    @POST("users/{username}/follows/{username2}")
    Call<User> createFollows(@Path("username") String username,
                             @Path("username2") String username2);

    @GET("users/{username}/posts")
    Call<List<Post>> getPosts(@Path("username") String username);

    @POST("users/{username}/posts")
    Call<Post> createPost(@Body Post post,
                          @Path("username") String username);

    @POST("users/{username}/posts/{username2}/{time}")
    Call<Post> createRePost(@Path("username") String username,
                            @Path("username2") String username2,
                            @Path("time") Long time);

    @GET("users/{username}/likes")
    Call<List<Post>> getLikes(@Path("username") String username);

    @POST("users/{username}/likes/{username2}/{time}")
    Call<Post> createLikes(@Path("username") String username,
                            @Path("username2") String username2,
                            @Path("time") Long time);

    @GET("users/{username}/timeline")
    Call<List<Post>> getTimeline(@Path("username") String username);

    @GET("users/{username}/recommendations/friends")
    Call<List<User>> recommendFriends(@Path("username") String username);

    @GET("users/{username}/recommendations/follows")
    Call<List<User>> recommendFollows(@Path("username") String username);
}