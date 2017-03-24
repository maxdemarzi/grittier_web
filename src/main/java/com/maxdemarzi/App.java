package com.maxdemarzi;

import com.maxdemarzi.models.Post;
import com.maxdemarzi.models.User;
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
      use(new Hbs("/templates", ".html"));

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
              Results.html("login");
          } else {
              throw new Err(Status.CONFLICT, "User already registered.");
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
          Response<User> response = service.getProfile(req.get("username")).execute();
          if (response.isSuccessful()) {
              User user = response.body();
              return Results.html("home").put("user", user);
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
