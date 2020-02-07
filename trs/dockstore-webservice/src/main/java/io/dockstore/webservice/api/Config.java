package io.dockstore.webservice.api;

import java.lang.reflect.InvocationTargetException;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.swagger.annotations.ApiModel;
import org.apache.commons.beanutils.BeanUtils;

@ApiModel(description = "Configuration information for UI clients of the Dockstore webservice.")
public final class Config extends DockstoreWebserviceConfiguration.UIConfig {

    /**
     * Properties that aren't in UIConfig
     */

    private String githubClientId;
    private String quayIoClientId;
    private String bitBucketClientId;
    private String gitlabClientId;
    private String zenodoClientId;
    private String googleClientId;
    private String discourseUrl;


    private Config() {
    }

    public static Config fromWebConfig(DockstoreWebserviceConfiguration webConfig)
            throws InvocationTargetException, IllegalAccessException {
        final Config config = new Config();
        config.githubClientId = webConfig.getGithubClientID();
        config.quayIoClientId = webConfig.getQuayClientID();
        config.bitBucketClientId = webConfig.getBitbucketClientID();
        config.gitlabClientId = webConfig.getGitlabClientID();
        config.zenodoClientId = webConfig.getZenodoClientID();
        config.googleClientId = webConfig.getGoogleClientID();
        config.discourseUrl = webConfig.getDiscourseUrl();
        BeanUtils.copyProperties(config, webConfig.getUiConfig());
        return config;
    }

    public String getGithubClientId() {
        return githubClientId;
    }

    public String getQuayIoClientId() {
        return quayIoClientId;
    }

    public String getBitBucketClientId() {
        return bitBucketClientId;
    }

    public String getGitlabClientId() {
        return gitlabClientId;
    }

    public String getZenodoClientId() {
        return zenodoClientId;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getDiscourseUrl() {
        return discourseUrl;
    }
}
