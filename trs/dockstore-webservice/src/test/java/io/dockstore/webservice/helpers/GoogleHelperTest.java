package io.dockstore.webservice.helpers;

import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class GoogleHelperTest {

    private static final String SUFFIX = "-abcdefghijklmnopqrstuvwxyz123456.apps.googleusercontent.com";
    private static final String AUDIENCE1 = "123456789012" + SUFFIX;
    private static final String EXTERNAL_PREFIX = "987654321098";
    private static final String EXTERNAL_AUDIENCE = EXTERNAL_PREFIX + SUFFIX;
    private static final String INVALID_AUDIENCE = "extremelyunlikelyaudiencewithoutadash";

    @Test
    public void isValidAudience() {
        final DockstoreWebserviceConfiguration config = new DockstoreWebserviceConfiguration();
        config.setGoogleClientID(AUDIENCE1);
        config.getExternalGoogleClientIdPrefixes().add(EXTERNAL_PREFIX);
        GoogleHelper.setConfig(config);
        final Tokeninfo tokeninfo = Mockito.mock(Tokeninfo.class);
        when(tokeninfo.getAudience()).thenReturn(AUDIENCE1).thenReturn(EXTERNAL_AUDIENCE).thenReturn(INVALID_AUDIENCE);
        Assert.assertTrue(GoogleHelper.isValidAudience(tokeninfo));
        Assert.assertTrue(GoogleHelper.isValidAudience(tokeninfo));
        Assert.assertFalse(GoogleHelper.isValidAudience(tokeninfo));
    }

    @Test
    public void updateUserFromGoogleUserinfoplus() {
        String pictureUrl = "https://example.com/picture";
        final String email = "jdoe@example.com";
        final String username = "Jane Doe";

        final User user = new User();
        final Userinfoplus userinfoplus = Mockito.mock(Userinfoplus.class);
        when(userinfoplus.getPicture()).thenReturn(pictureUrl);
        when(userinfoplus.getEmail()).thenReturn(email);
        when(userinfoplus.getName()).thenReturn(username);
        GoogleHelper.updateUserFromGoogleUserinfoplus(userinfoplus, user);
        Assert.assertEquals(pictureUrl, user.getAvatarUrl());
        final User.Profile profile = user.getUserProfiles().get(TokenType.GOOGLE_COM.toString());
        Assert.assertEquals(email, profile.email);
        Assert.assertEquals(username, profile.name);
        Assert.assertEquals(pictureUrl, profile.avatarURL);
    }
}
