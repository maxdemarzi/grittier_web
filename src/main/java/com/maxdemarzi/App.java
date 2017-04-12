package com.maxdemarzi;

import com.maxdemarzi.models.Post;
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

/**
 * @author jooby generator
 */
public class App extends Jooby {
  public static GritterService service;

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

          service = retrofit.create(GritterService.class);
      });

      // Configure public static files
      assets("/assets/**");

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
              return  views.users.template(user, users);
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
              return views.users.template(user, users);
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
              return views.home.template(user, posts, new ArrayList<User>());
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
              return views.user.template(user, posts);
          } else {
              throw new Err(Status.BAD_REQUEST);
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

              Response<List<User>> recommendationsResponse = service.recommendFollows(req.get("id")).execute();
              List<User> recommendations = new ArrayList<>();
              if(recommendationsResponse.isSuccessful()) {
                  recommendations = recommendationsResponse.body();
              }

              return views.home.template(user, posts, recommendations);
          } else {
              throw new Err(Status.BAD_REQUEST);
          }

      });

      post("/post", req -> {
          Post post = req.form(Post.class);
          Response<Post> response = service.createPost(post, req.get("id")).execute();
          if (response.isSuccessful()) {
              return response.body();
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      }).produces("json");

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

  public static void main(final String[] args) {
    run(App::new, args);
  }

}
