package com.maxdemarzi;

import com.maxdemarzi.models.Post;
import com.maxdemarzi.models.Tag;
import com.maxdemarzi.models.User;
import com.typesafe.config.Config;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jooby.Err;
import org.jooby.Jooby;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.json.Jackson;
import org.jooby.pac4j.Auth;
import org.jooby.rocker.Rockerby;
import org.jooby.whoops.Whoops;
import org.mindrot.jbcrypt.BCrypt;
import org.pac4j.core.profile.UserProfile;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import views.index;
import views.login;
import views.register;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static java.lang.Thread.sleep;

/**
 * @author jooby generator
 */
public class App extends Jooby {
  public static GrittierService service;

  {
      // Debug friendly error messages
      on("dev", () -> use(new Whoops()));

      // Configure Jackson
      use(new Jackson().doWith(mapper -> {
          mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
      }));

      // Setup Template Engine
      use(new Rockerby());

      // Setup Service
      onStart(registry -> {

          Config conf = require(Config.class);

          // Define the interceptor, add authentication headers
          String credentials = Credentials.basic(conf.getString("neo4j.username"), conf.getString("neo4j.password"));
          Interceptor interceptor = chain -> {
              Request newRequest = chain.request().newBuilder().addHeader("Authorization", credentials).build();
              return chain.proceed(newRequest);
          };

          // Add the interceptor to OkHttpClient
          OkHttpClient.Builder builder = new OkHttpClient.Builder();
          builder.interceptors().add(interceptor);
          OkHttpClient client = builder.build();

          Retrofit retrofit = new Retrofit.Builder()
                  .client(client)
                  .baseUrl("http://" + conf.getString("neo4j.url") + conf.getString("neo4j.prefix") +  "/")
                  .addConverterFactory(JacksonConverterFactory.create())
                  .build();

          service = retrofit.create(GrittierService.class);
      });

      // Configure public static files
      assets("/assets/**");
      assets("/favicon.ico", "/assets/favicon.ico");

      get("/", index::template);
      get("/login", login::template);
      get("/register", register::template);
      post("/register", (req, rsp) -> {
          User user = req.form(User.class);
          user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
          Response<User> response = service.createUser(user).execute();
          if (response.isSuccessful()) {
              Results.redirect("/login");
          } else {
              throw new Err(Status.CONFLICT, "There was a problem with your registration.");
          }
      });


      get("/user/{username}/following/", req -> {
          String id = req.session().get("id").value(null);
          Response<User> userResponse = service.getProfile(req.param("username").value(), id).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<User>> usersResponse = service.getFollowing(req.param("username").value()).execute();
              List<User> users = new ArrayList<>();
              if (usersResponse.isSuccessful()) {
                  users = usersResponse.body();
              }
              return  views.users.template(user, users, getUsersToFollow(id), getTags());
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/user/{username}/followers/", req -> {
          String id = req.session().get("id").value(null);
          Response<User> userResponse = service.getProfile(req.param("username").value(), id).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<User>> usersResponse = service.getFollowers(req.param("username").value()).execute();
              List<User> users = new ArrayList<>();
              if (usersResponse.isSuccessful()) {
                  users = usersResponse.body();
              }

              return views.users.template(user, users, getUsersToFollow(id), getTags());
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/user/{username}/likes", req -> {
          String id = req.session().get("id").value(null);
          Response<User> userResponse = service.getProfile(req.param("username").value(), id).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<Post>> timelineResponse = service.getLikes(req.param("username").value()).execute();
              List<Post> posts = new ArrayList<>();
              if (timelineResponse.isSuccessful()) {
                  posts = timelineResponse.body();
              }
              return views.home.template(user, posts, getUsersToFollow(id), getTags());
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/user/{username}", req -> {
          String id = req.session().get("id").value(null);
          Response<User> userResponse = service.getProfile(req.param("username").value(), id).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<Post>> timelineResponse = service.getPosts(req.param("username").value()).execute();
              List<Post> posts = new ArrayList<>();
              if (timelineResponse.isSuccessful()) {
                  posts = timelineResponse.body();
              }

              return views.user.template(user, posts, getUsersToFollow(id), getTags());
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/tag/{hashtag}", req -> {
          String id = req.session().get("id").value(null);
          if (id != null) {
              Response<User> userResponse = service.getProfile(id, null).execute();
              if (userResponse.isSuccessful()) {
                  User user = userResponse.body();

                  Response<List<Post>> tagResponse = service.getTag(req.param("hashtag").value(), id).execute();
                  List<Post> posts = new ArrayList<>();
                  if (tagResponse.isSuccessful()) {
                      posts = tagResponse.body();
                  }

                  return views.home.template(user, posts, getUsersToFollow(id), getTags());
              } else {
                  throw new Err(Status.BAD_REQUEST);
              }
          } else {
              Response<List<Post>> tagResponse = service.getTag(req.param("q").value(), null).execute();
              List<Post> posts = new ArrayList<>();
              if (tagResponse.isSuccessful()) {
                  posts = tagResponse.body();
              }

              return views.home.template(null, posts, getUsersToFollow(id), getTags());
          }
      });

      post("/search", req -> {
          String id = req.session().get("id").value(null);
          if (id != null) {
              Response<User> userResponse = service.getProfile(id, null).execute();
              if (userResponse.isSuccessful()) {
                  User user = userResponse.body();

                  Response<List<Post>> searchResponse = service.getSearch(req.param("q").value(), id).execute();
                  List<Post> posts = new ArrayList<>();
                  if (searchResponse.isSuccessful()) {
                      posts = searchResponse.body();
                  }

                  return views.home.template(user, posts, getUsersToFollow(id), getTags());
              } else {
                  throw new Err(Status.BAD_REQUEST);
              }
          } else {
              Response<List<Post>> searchResponse = service.getSearch(req.param("q").value(), null).execute();
              List<Post> posts = new ArrayList<>();
              if (searchResponse.isSuccessful()) {
                  posts = searchResponse.body();
              }

              return views.home.template(null, posts, getUsersToFollow(id), getTags());
          }
      });

      get("/explore", req -> {
          String id = req.session().get("id").value(null);
          if (id != null) {
              Response<User> userResponse = service.getProfile(id, null).execute();
              if (userResponse.isSuccessful()) {
                  User user = userResponse.body();

                  Response<List<Post>> searchResponse = service.getLatest(id).execute();
                  List<Post> posts = new ArrayList<>();
                  if (searchResponse.isSuccessful()) {
                      posts = searchResponse.body();
                  }

                  return views.home.template(user, posts, getUsersToFollow(id), getTags());
              } else {
                  throw new Err(Status.BAD_REQUEST);
              }
          } else {
              Response<List<Post>> searchResponse = service.getLatest(null).execute();
              List<Post> posts = new ArrayList<>();
              if (searchResponse.isSuccessful()) {
                  posts = searchResponse.body();
              }

              return views.home.template(null, posts, getUsersToFollow(id), getTags());
          }
      });

      use(new Auth().form("*", ServiceAuthenticator.class));


      // Set the username.
      get("*", (req, rsp, chain) -> {
          UserProfile profile = require(UserProfile.class);
          req.set("id", profile.getId());
          chain.next(req, rsp);
      });

      post("*", (req, rsp, chain) -> {
          UserProfile profile = require(UserProfile.class);
          req.set("id", profile.getId());
          chain.next(req, rsp);
      });

      get("/home", req -> {
          Response<User> userResponse = service.getProfile(req.get("id"), null).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<Post>> timelineResponse = service.getTimeline(req.get("id")).execute();
              List<Post> posts = new ArrayList<>();
              if (timelineResponse.isSuccessful()) {
                  posts = timelineResponse.body();
              }

              return views.home.template(user, posts,  getUsersToFollow(req.get("id")), getTags());
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      post("/post", req -> {
          Post post = req.form(Post.class);
          Response<Post> response = service.createPost(post, req.get("id")).execute();
          if (response.isSuccessful()) {
              sleep(1000);
              return Results.redirect("/home");
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      post("/like/{username}/{time}", req -> {
          Response<Post> response = service.createLikes(req.get("id"), req.param("username").value(), req.param("time").longValue()).execute();
          if (response.isSuccessful()) {
              return response.body();
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      }).produces("json");

      post("/post/{username}/{time}", req -> {
          Response<Post> response = service.createRePost(req.get("id"), req.param("username").value(), req.param("time").longValue()).execute();
          if (response.isSuccessful()) {
              return response.body();
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      }).produces("json");

      post("/follow/{username}", req -> {
          Response<User> response = service.createFollows(req.get("id"), req.param("username").value()).execute();
          if (response.isSuccessful()) {
              return response.body();
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      }).produces("json");

      delete("/follow/{username}", req -> {
          Response<User> response = service.removeFollows(req.get("id"), req.param("username").value()).execute();
          if (response.isSuccessful()) {
              return response.body();
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      }).produces("json");
  }

    private List<User> getUsersToFollow(String id) throws java.io.IOException {
        List<User> recommendations = new ArrayList<>();
        if (id != null) {
            Response<List<User>> recommendationsResponse = service.recommendFollows(id).execute();
            if (recommendationsResponse.isSuccessful()) {
                recommendations = recommendationsResponse.body();
            }
        }
        return recommendations;
    }

    private List<Tag> getTags() throws java.io.IOException {
        List<Tag> trends = new ArrayList<>();
        Response<List<Tag>> trendsResponce = service.getTags().execute();
        if (trendsResponce.isSuccessful()) {
            trends = trendsResponce.body();
        }
        return trends;
    }

    public static void main(final String[] args) {
    run(App::new, args);
  }

}
