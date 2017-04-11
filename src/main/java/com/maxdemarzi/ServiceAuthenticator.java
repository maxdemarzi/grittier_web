package com.maxdemarzi;

import com.maxdemarzi.models.User;
import org.mindrot.jbcrypt.BCrypt;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.credentials.authenticator.Authenticator;
import org.pac4j.core.exception.AccountNotFoundException;
import org.pac4j.core.exception.BadCredentialsException;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.IOException;

import static com.maxdemarzi.App.service;

public class ServiceAuthenticator implements Authenticator<UsernamePasswordCredentials> {
    private static final Logger logger = LoggerFactory.getLogger(ServiceAuthenticator.class);

    @Override
    public void validate(UsernamePasswordCredentials credentials, WebContext webContext) throws HttpAction {
        if (credentials == null) {
            throwException("No credentials");
            return;
        }

        String username = credentials.getUsername();
        if (CommonHelper.isBlank(username)) {
            throwException("Username cannot be blank");
        }

        String password = credentials.getPassword();
        if (CommonHelper.isBlank(password)) {
            throwException("Password cannot be blank");
        }

        Response<User> response;
        try {
            response = service.getUser(username).execute();
            User user = response.body();
            if (!BCrypt.checkpw(credentials.getPassword(), user.getPassword())){
                String message = "Bad credentials for: " + username;
                logger.error(message);
                throw new BadCredentialsException(message);
            } else {
                CommonProfile profile = new CommonProfile();
                profile.setId(username);
                profile.addAttribute("name", user.getName());
                profile.addAttribute("email", user.getEmail());
                credentials.setUserProfile(profile);
                webContext.setSessionAttribute("id", username);
            }
        } catch (IOException e) {
            String message = "No account found for: " + username;
            logger.error(message);
            throw new AccountNotFoundException(message);
        }
    }

    private void throwException(String message) throws CredentialsException {
        logger.error(message);
        throw new CredentialsException(message);
    }
}
