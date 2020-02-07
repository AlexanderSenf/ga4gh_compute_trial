/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.resources;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.PrivacyPolicyVersion;
import io.dockstore.webservice.core.TOSVersion;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * The githubToken resource handles operations with tokens. Tokens are needed to talk with the quay.io and github APIs. In addition, they
 * will be needed to pull down docker containers that are requested by users.
 *
 * @author dyuen
 */
@Path("/auth/tokens")
@Api(value = "/auth/tokens", tags = "tokens")
@Produces(MediaType.APPLICATION_JSON)
@io.swagger.v3.oas.annotations.tags.Tag(name = "tokens", description = ResourceConstants.TOKENS)
public class TokenResource implements AuthenticatedResourceInterface, SourceControlResourceInterface {
    /**
     * Global instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final String QUAY_URL = "https://quay.io/api/v1/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";
    private static final String GITLAB_URL = "https://gitlab.com/";
    private static final TOSVersion CURRENT_TOS_VERSION = TOSVersion.TOS_VERSION_1;
    private static final PrivacyPolicyVersion CURRENT_PRIVACY_POLICY_VERSION = PrivacyPolicyVersion.PRIVACY_POLICY_VERSION_2_5;
    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);

    private final TokenDAO tokenDAO;
    private final UserDAO userDAO;

    private final String githubClientID;
    private final String githubClientSecret;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final String gitlabClientID;
    private final String gitlabRedirectUri;
    private final String gitlabClientSecret;
    private final String zenodoClientID;
    private final String zenodoRedirectUri;
    private final String zenodoUrl;
    private final String zenodoAuthUrl;
    private final String zenodoClientSecret;
    private final String googleClientID;
    private final String googleClientSecret;
    private final HttpClient client;
    private final CachingAuthenticator<String, User> cachingAuthenticator;

    public TokenResource(TokenDAO tokenDAO, UserDAO enduserDAO, HttpClient client, CachingAuthenticator<String, User> cachingAuthenticator,
            DockstoreWebserviceConfiguration configuration) {
        this.tokenDAO = tokenDAO;
        userDAO = enduserDAO;
        this.githubClientID = configuration.getGithubClientID();
        this.githubClientSecret = configuration.getGithubClientSecret();
        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        this.gitlabClientID = configuration.getGitlabClientID();
        this.gitlabClientSecret = configuration.getGitlabClientSecret();
        this.gitlabRedirectUri = configuration.getGitlabRedirectURI();
        this.zenodoClientID = configuration.getZenodoClientID();
        this.zenodoClientSecret = configuration.getZenodoClientSecret();
        this.zenodoRedirectUri = configuration.getZenodoRedirectURI();
        this.zenodoUrl = configuration.getZenodoUrl();
        this.zenodoAuthUrl = configuration.getUiConfig().getZenodoAuthUrl();
        this.googleClientID = configuration.getGoogleClientID();
        this.googleClientSecret = configuration.getGoogleClientSecret();
        this.client = client;
        this.cachingAuthenticator = cachingAuthenticator;
    }

    @GET
    @Path("/{tokenId}")
    @Timed
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "Get a specific token by id.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Token.class)
    @ApiResponses({ @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid ID supplied"),
            @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Token not found") })
    public Token listToken(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("ID of token to return") @PathParam("tokenId") Long tokenId) {
        Token token = tokenDAO.findById(tokenId);
        checkUser(user, token.getUserId());

        return token;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @ApiOperation(value = "Add a new quay IO token.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addQuayToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("access_token") String accessToken) {
        if (accessToken.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access token.", HttpStatus.SC_BAD_REQUEST);
        }

        String url = QUAY_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString);

        if (user != null) {
            List<Token> tokens = tokenDAO.findQuayByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.QUAY_IO);
                token.setContent(accessToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Quay.io tokenusername is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Quay token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Quay token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Quay token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }

    @DELETE
    @Path("/{tokenId}")
    @UnitOfWork
    @ApiOperation(value = "Delete a token.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid token value"))
    public Response deleteToken(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Token id to delete", required = true) @PathParam("tokenId") Long tokenId) {
        Token token = tokenDAO.findById(tokenId);
        checkUser(user, token.getUserId());

        // invalidate cache now that we're deleting the token
        cachingAuthenticator.invalidate(token.getContent());

        tokenDAO.delete(token);

        token = tokenDAO.findById(tokenId);
        if (token == null) {
            return Response.noContent().build();
        } else {
            return Response.serverError().build();
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/gitlab.com")
    @ApiOperation(value = "Add a new gitlab.com token.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGitlabToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("code") String code) {
        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(GITLAB_URL + "oauth/token"),
                new ClientParametersAuthentication(gitlabClientID, gitlabClientSecret), gitlabClientID, GITLAB_URL + "oauth/authorize")
                .build();

        LOG.info("About to try and grab access token");
        String accessToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).setGrantType("authorization_code")
                    .setRedirectUri(gitlabRedirectUri).execute();
            accessToken = tokenResponse.getAccessToken();
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve gitlab.com token based on code", HttpStatus.SC_BAD_REQUEST);
        }

        String url = GITLAB_URL + "api/v3/user";

        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString);

        if (user != null) {
            List<Token> tokens = tokenDAO.findGitlabByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.GITLAB_COM);
                token.setContent(accessToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Gitlab.com tokenusername is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Gitlab token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Gitlab token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Gitlab token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }

    }


    private String getCodeFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject oauthData = satellizerObject.get("oauthData").getAsJsonObject();
        return oauthData.get("code").getAsString();
    }

    private String getRedirectURIFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject authorizationData = satellizerObject.get("authorizationData").getAsJsonObject();
        return authorizationData.get("redirect_uri").getAsString();
    }

    private boolean getRegisterFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject userData = satellizerObject.get("userData").getAsJsonObject();
        return userData.has("register") && userData.get("register").getAsBoolean();
    }

    /**
     * Adds a Google token to the existing user if user is authenticated already.
     * Otherwise, below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</b></td> <td><b>Have GitHub account with Google token</b></td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</b></td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</b></td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</b></td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     *
     * @param authUser          The optional Dockstore-authenticated user
     * @param satellizerJson    Satellizer object returned by satellizer
     * @return The user's Dockstore token
     */
    @POST
    @Timed
    @UnitOfWork
    @Path("/google")
    @ApiOperation(value = "Allow satellizer to post a new Google token to Dockstore.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "A post method is required by satellizer to send the Google token", response = Token.class)
    public Token addGoogleToken(@ApiParam(hidden = true) @Auth Optional<User> authUser, @ApiParam("code") String satellizerJson) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(satellizerJson, JsonElement.class);
        JsonObject satellizerObject = element.getAsJsonObject();
        final String code = getCodeFromSatellizerObject(satellizerObject);
        final String redirectUri = getRedirectURIFromSatellizerObject(satellizerObject);
        final boolean registerUser = getRegisterFromSatellizerObject(satellizerObject);
        TokenResponse tokenResponse = GoogleHelper.getTokenResponse(googleClientID, googleClientSecret, code, redirectUri);
        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();
        LOG.info("Token expires in " + tokenResponse.getExpiresInSeconds().toString() + " seconds.");
        Userinfoplus userinfo = getUserInfo(accessToken);
        long userID;
        Token dockstoreToken = null;
        Token googleToken = null;
        String googleLoginName = userinfo.getEmail();
        User user = userDAO.findByGoogleEmail(googleLoginName);

        if (registerUser && authUser.isEmpty()) {
            if (user == null) {
                user = new User();
                // Pull user information from Google
                user.setUsername(userinfo.getEmail());
                userID = userDAO.create(user);
            } else {
                throw new CustomWebApplicationException("User already exists, cannot register new user", HttpStatus.SC_FORBIDDEN);
            }
        } else {
            if (authUser.isPresent()) {
                userID = authUser.get().getId();
            } else if (user != null) {
                userID = user.getId();
            } else {
                throw new CustomWebApplicationException("Login failed, you may need to register an account", HttpStatus.SC_UNAUTHORIZED);
            }

            List<Token> tokens = tokenDAO.findDockstoreByUserId(userID);
            if (!tokens.isEmpty()) {
                dockstoreToken = tokens.get(0);
            }

            tokens = tokenDAO.findGoogleByUserId(userID);
            if (!tokens.isEmpty()) {
                googleToken = tokens.get(0);
            }
        }

        user = userDAO.findById(userID);
        acceptTOSAndPrivacyPolicy(user);

        if (dockstoreToken == null) {
            LOG.info("Could not find user's dockstore token. Making new one...");
            dockstoreToken = createDockstoreToken(userID, user.getUsername());
        }

        if (googleToken == null) {
            LOG.info("Could not find user's Google token. Making new one...");
            // CREATE GOOGLE TOKEN
            googleToken = new Token(accessToken, refreshToken, userID, googleLoginName, TokenType.GOOGLE_COM);
            tokenDAO.create(googleToken);
            // Update user profile too
            user = userDAO.findById(userID);
            GoogleHelper.updateUserFromGoogleUserinfoplus(userinfo, user);
            LOG.info("Google token created for {}", googleLoginName);
        } else {
            // Update tokens if exists
            googleToken.setContent(accessToken);
            googleToken.setRefreshToken(refreshToken);
            tokenDAO.update(googleToken);
        }
        return dockstoreToken;
    }

    private void acceptTOSAndPrivacyPolicy(User user) {
        Date date = new Date();
        if (user.getTOSVersion() != CURRENT_TOS_VERSION) {
            user.setTOSVersion(CURRENT_TOS_VERSION);
            user.setTOSVersionAcceptanceDate((date));
        }
        if (user.getPrivacyPolicyVersion() != CURRENT_PRIVACY_POLICY_VERSION) {
            user.setPrivacyPolicyVersion(CURRENT_PRIVACY_POLICY_VERSION);
            user.setPrivacyPolicyVersionAcceptanceDate(date);
        }
    }

    /**
     * Get the Google Userinfoplus object
     *
     * @param accessToken Google access token
     * @return
     */
    private Userinfoplus getUserInfo(String accessToken) {
        Optional<Userinfoplus> userinfoplus = GoogleHelper.userinfoplusFromToken(accessToken);
        if (userinfoplus.isPresent()) {
            return userinfoplus.get();
        } else {
            throw new CustomWebApplicationException("Could not get Google user info using token.", HttpStatus.SC_EXPECTATION_FAILED);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/github")
    @ApiOperation(value = "Allow satellizer to post a new GitHub token to dockstore, used by login, can create new users.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "A post method is required by satellizer to send the GitHub token", response = Token.class)
    public Token addToken(@ApiParam("code") String satellizerJson) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(satellizerJson, JsonElement.class);
        JsonObject satellizerObject = element.getAsJsonObject();
        final String code = getCodeFromSatellizerObject(satellizerObject);
        final boolean registerUser = getRegisterFromSatellizerObject(satellizerObject);
        return handleGitHubUser(null, code, registerUser);
    }
    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @ApiOperation(value = "Add a new github.com token, used by accounts page.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGithubToken(@ApiParam(hidden = true) @Auth User authUser, @QueryParam("code") String code) {
        // never create a new user via account linking page
        return handleGitHubUser(authUser, code, false);
    }

    private Token handleGitHubUser(User authUser, String code, boolean registerUser) {
        String accessToken = GitHubHelper.getGitHubAccessToken(code, this.githubClientID, this.githubClientSecret);

        String githubLogin;
        Token dockstoreToken = null;
        Token githubToken = null;
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(accessToken).build();
            githubLogin = github.getMyself().getLogin();
        } catch (IOException ex) {
            throw new CustomWebApplicationException("Token ignored due to IOException", HttpStatus.SC_CONFLICT);
        }

        User user = userDAO.findByGitHubUsername(githubLogin);
        long userID;
        if (registerUser) {
            // check that there was no previous user, but by default use the github login
            String username = githubLogin;
            int count = 1;
            while (userDAO.findByUsername(username) != null) {
                username = githubLogin + count++;
            }

            if (user == null && authUser == null) {
                User newUser = new User();
                newUser.setUsername(username);
                userID = userDAO.create(newUser);
                user = userDAO.findById(userID);
            } else {
                throw new CustomWebApplicationException("User already exists, cannot register new user", HttpStatus.SC_FORBIDDEN);
            }
        } else {
            if (authUser != null) {
                userID = authUser.getId();
            } else if (user != null) {
                userID = user.getId();
            } else {
                throw new CustomWebApplicationException("Login failed, you may need to register an account", HttpStatus.SC_UNAUTHORIZED);
            }
            List<Token> tokens = tokenDAO.findDockstoreByUserId(userID);
            if (!tokens.isEmpty()) {
                dockstoreToken = tokens.get(0);
            }

            tokens = tokenDAO.findGithubByUserId(userID);
            if (!tokens.isEmpty()) {
                githubToken = tokens.get(0);
            }
        }
        // check that user has accepted the latest version of the TOS and privacy policy. If not, update since acceptance for both is passively done by logging in/registering
        user = userDAO.findById(userID);
        acceptTOSAndPrivacyPolicy(user);

        if (dockstoreToken == null) {
            LOG.info("Could not find user's dockstore token. Making new one...");
            dockstoreToken = createDockstoreToken(userID, user.getUsername());
        }

        if (githubToken == null) {
            LOG.info("Could not find user's github token. Making new one...");
            // CREATE GITHUB TOKEN
            githubToken = new Token();
            githubToken.setTokenSource(TokenType.GITHUB_COM);
            githubToken.setContent(accessToken);
            githubToken.setUserId(userID);
            githubToken.setUsername(githubLogin);
            tokenDAO.create(githubToken);
            user = userDAO.findById(userID);
            GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(githubToken, null);
            gitHubSourceCodeRepo.getUserMetadata(user);
        }
        return dockstoreToken;
    }



    private Token createDockstoreToken(long userID, String githubLogin) {
        Token dockstoreToken;
        final Random random = new Random();
        final int bufferLength = 1024;
        final byte[] buffer = new byte[bufferLength];
        random.nextBytes(buffer);
        String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
        final String dockstoreAccessToken = Hashing.sha256().hashString(githubLogin + randomString, Charsets.UTF_8).toString();

        dockstoreToken = new Token();
        dockstoreToken.setTokenSource(TokenType.DOCKSTORE);
        dockstoreToken.setContent(dockstoreAccessToken);
        dockstoreToken.setUserId(userID);
        dockstoreToken.setUsername(githubLogin);
        long dockstoreTokenId = tokenDAO.create(dockstoreToken);
        dockstoreToken = tokenDAO.findById(dockstoreTokenId);
        return dockstoreToken;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org")
    @ApiOperation(value = "Add a new bitbucket.org token, used by quay.io redirect.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addBitbucketToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("code") String code) {
        if (code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access code", HttpStatus.SC_BAD_REQUEST);
        }

        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(BITBUCKET_URL + "site/oauth2/access_token"),
                new ClientParametersAuthentication(bitbucketClientID, bitbucketClientSecret), bitbucketClientID,
                "https://bitbucket.org/site/oauth2/authorize").build();

        String accessToken;
        String refreshToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code).setScopes(Collections.singletonList("user:email"))
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute();
            accessToken = tokenResponse.getAccessToken();
            refreshToken = tokenResponse.getRefreshToken();
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve bitbucket.org token based on code", HttpStatus.SC_BAD_REQUEST);
        }

        String url = BITBUCKET_URL + "api/2.0/user";
        Optional<String> asString2 = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString2);

        if (user != null) {
            List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.BITBUCKET_ORG);
                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Bitbucket.org token username is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Bitbucket token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Bitbucket token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Bitbucket token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }



    @GET
    @Timed
    @UnitOfWork
    @Path("/zenodo.org")
    @ApiOperation(value = "Add a new zenodo.org token, used by accounts page.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addZenodoToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("code") String code) {
        if (code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide a Zenodo access code", HttpStatus.SC_BAD_REQUEST);
        }

        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(zenodoUrl + "/oauth/token"),
                new ClientParametersAuthentication(zenodoClientID, zenodoClientSecret), zenodoClientID,
                zenodoAuthUrl).build();

        LOG.info("About to request zenodo access token");
        String accessToken;
        String refreshToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).setGrantType("authorization_code")
                    .setRedirectUri(zenodoRedirectUri).execute();
            accessToken = tokenResponse.getAccessToken();
            refreshToken = tokenResponse.getRefreshToken();
        } catch (IOException e) {
            LOG.error("Retrieving zenodo access token was unsuccessful.", e);
            throw new CustomWebApplicationException("Could not retrieve zenodo token based on code " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
        }

        if (user != null) {
            List<Token> tokens = tokenDAO.findZenodoByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.ZENODO_ORG);
                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);
                token.setUserId(user.getId());
                // Zenodo does not return a user name in the token response
                // so set the token user name to the Dockstore user name
                // otherwise we will get a DB error when trying to
                // link another user's Zenodo credentials
                token.setUsername(user.getUsername());
                long create = tokenDAO.create(token);
                LOG.info("Zenodo token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Zenodo token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Zenodo token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_NOT_FOUND);
        }
    }




    private String getUserName(String url, Optional<String> asString2) {
        String username;
        if (asString2.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);

            String response = asString2.get();
            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>)gson.fromJson(response, map.getClass());

            username = map.get("username");
            LOG.info("Username: {}", username);
            return username;
        }
        throw new CustomWebApplicationException("User not found", HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }
}
