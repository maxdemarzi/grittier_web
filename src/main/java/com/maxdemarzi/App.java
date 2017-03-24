package com.maxdemarzi;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.HumanizeHelper;
import com.maxdemarzi.models.Post;
import com.maxdemarzi.models.User;
import humanize.Humanize;
import humanize.emoji.EmojiApi;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jooby.Err;
import org.jooby.Jooby;
import org.jooby.Results;
import org.jooby.Status;
import org.jooby.hbs.Hbs;
import org.jooby.json.Jackson;
import org.jooby.pac4j.Auth;
import org.jooby.whoops.Whoops;
import org.mindrot.jbcrypt.BCrypt;
import org.pac4j.core.profile.UserProfile;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.ArrayList;
import java.util.Date;
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
      use(new Hbs("/templates", ".html")
              .doWith(hbs -> {
                  HumanizeHelper.register(hbs);
                  hbs.registerHelper("humanTime",
                          (Helper<Long>) (context, options) -> Humanize.naturalTime(new Date(context * 1000)));

                  EmojiApi.configure().assetsURL("http://localhost/assets/");
                  hbs.registerHelper("emoji",
                          (Helper<String>) (context, options) -> EmojiApi.replaceUnicodeWithImages("0x" + context));
                                  //EmojiApi.imageTagByUnicode("❤"));

                  //EmojiApi.byHexCode(context).getSources());
              })
      );

      // Setup Service
      onStart(registry -> {
          // Define the interceptor, add authentication headers
          String credentials = Credentials.basic("neo4j", "swordfish");
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
                  .baseUrl("http://localhost:7474/v1/")
                  .addConverterFactory(JacksonConverterFactory.create())
                  .build();

          service = retrofit.create(GritterService.class);
      });

      // Configure public static files
      assets("/assets/**");
      assets("/favicon.ico", "/assets/favicon.ico");

      get("/", () -> "Hello World!");
      get("/register", () -> Results.html("register"));
      post("/register", (req, rsp) -> {
          User user = req.form(User.class);
          user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
          Response<User> response = service.createUser(user).execute();
          if (response.isSuccessful()) {
              Results.redirect("/login");
          } else {
              throw new Err(Status.CONFLICT, "User already registered.");
          }
      });

      get("/{username}/posts/", req -> {
          Response<User> userResponse = service.getProfile(req.param("username").value()).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<Post>> timelineResponse = service.getPosts(req.param("username").value()).execute();
              List<Post> posts = new ArrayList<>();
              if (timelineResponse.isSuccessful()) {
                  posts = timelineResponse.body();
              }
              return Results.html("home")
                      .put("user", user)
                      .put("posts", posts);
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/{username}/following/", req -> {
          Response<User> userResponse = service.getProfile(req.param("username").value()).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<User>> usersResponse = service.getFollowing(req.param("username").value()).execute();
              List<User> users = new ArrayList<>();
              if (usersResponse.isSuccessful()) {
                  users = usersResponse.body();
              }
              return Results.html("users")
                      .put("user", user)
                      .put("users", users);
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      get("/{username}/followers/", req -> {
          Response<User> userResponse = service.getProfile(req.param("username").value()).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<User>> usersResponse = service.getFollowers(req.param("username").value()).execute();
              List<User> users = new ArrayList<>();
              if (usersResponse.isSuccessful()) {
                  users = usersResponse.body();
              }
              return Results.html("users")
                      .put("user", user)
                      .put("users", users);
          } else {
              throw new Err(Status.BAD_REQUEST);
          }
      });

      use(new Auth().form("*", ServiceAuthenticator.class));

      // Set the username.
      get("*", (req, rsp, chain) -> {
          UserProfile profile = require(UserProfile.class);
          req.set("username", profile.getId());
          chain.next(req, rsp);
      });

      post("*", (req, rsp, chain) -> {
          UserProfile profile = require(UserProfile.class);
          req.set("username", profile.getId());
          chain.next(req, rsp);
      });

      get("/home", req -> {
          Response<User> userResponse = service.getProfile(req.get("username")).execute();
          if (userResponse.isSuccessful()) {
              User user = userResponse.body();

              Response<List<Post>> timelineResponse = service.getTimeline(req.get("username")).execute();
              List<Post> posts = new ArrayList<>();
              if (timelineResponse.isSuccessful()) {
                  posts = timelineResponse.body();
              }

              Response<List<User>> recommendationsResponse = service.recommendFollows(req.get("username")).execute();
              List<User> recommendations = new ArrayList<>();
              if(recommendationsResponse.isSuccessful()) {
                  recommendations = recommendationsResponse.body();
              }

              return Results.html("home")
                      .put("user", user)
                      .put("posts", posts)
                      .put("recommendations", recommendations);
          } else {
              throw new Err(Status.BAD_REQUEST);
          }

      });

      post("/post", req -> {
          Post post = req.form(Post.class);
          Response<Post> response = service.createPost(post, req.get("username")).execute();
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
