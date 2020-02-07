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

package io.dockstore.webservice;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.cache.CacheBuilderSpec;
import io.dropwizard.Configuration;
import io.dropwizard.client.HttpClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import org.hibernate.validator.constraints.NotEmpty;

public class DockstoreWebserviceConfiguration extends Configuration {

    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @Valid
    @NotNull
    private HttpClientConfiguration httpClient = new HttpClientConfiguration();

    @Valid
    private ElasticSearchConfig esConfiguration = new ElasticSearchConfig();

    @Valid
    @NotNull
    private ExternalConfig externalConfig = new ExternalConfig();

    @Valid
    private SamConfiguration samConfiguration = new SamConfiguration();

    @Valid
    private LimitConfig limitConfig = new LimitConfig();

    @NotEmpty
    private String template;

    @NotEmpty
    private String quayClientID;

    @NotEmpty
    private String githubClientID;

    @NotEmpty
    private String googleClientID;

    @NotEmpty
    private String gitlabClientID;

    @NotEmpty
    private String bitbucketClientID;

    @NotEmpty
    private String bitbucketClientSecret;

    @NotEmpty
    private String quayRedirectURI;

    @NotEmpty
    @JsonProperty
    private String githubRedirectURI;

    @NotEmpty
    private String githubClientSecret;

    @NotEmpty
    private String googleRedirectURI;

    @NotEmpty
    private String googleClientSecret;

    @NotEmpty
    private String gitlabRedirectURI;

    @NotEmpty
    private String gitlabClientSecret;

    @NotEmpty
    private String zenodoClientID;

    @NotEmpty
    private String zenodoRedirectURI;

    @NotEmpty
    private String zenodoUrl;

    @NotEmpty
    private String zenodoClientSecret;

    @NotEmpty
    private String discourseUrl;

    @NotEmpty
    private String discourseKey;

    @NotNull
    private Integer discourseCategoryId;

    @NotNull
    private String gitHubAppId;

    @NotNull
    private String gitHubAppPrivateKeyFile;

    @NotNull
    private CacheBuilderSpec authenticationCachePolicy;

    private String languagePluginLocation;

    private String sqsURL;

    private String toolTesterBucket = null;

    private String authorizerType = null;

    private List<String> externalGoogleClientIdPrefixes = new ArrayList<>();

    @Valid
    @NotNull
    private UIConfig uiConfig;

    @JsonProperty("toolTesterBucket")
    public String getToolTesterBucket() {
        return toolTesterBucket;
    }

    @JsonProperty("database")
    public DataSourceFactory getDataSourceFactory() {
        return database;
    }

    @JsonProperty("httpClient")
    public HttpClientConfiguration getHttpClientConfiguration() {
        return httpClient;
    }

    @JsonProperty("externalConfig")
    public ExternalConfig getExternalConfig() {
        return externalConfig;
    }

    @JsonProperty
    public String getTemplate() {
        return template;
    }

    @JsonProperty
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * @return the quayClientID
     */
    @JsonProperty
    public String getQuayClientID() {
        return quayClientID;
    }

    /**
     * @param quayClientID the quayClientID to set
     */
    @JsonProperty
    public void setQuayClientID(String quayClientID) {
        this.quayClientID = quayClientID;
    }

    /**
     * @return the quayRedirectURI
     */
    @JsonProperty
    public String getQuayRedirectURI() {
        return quayRedirectURI;
    }

    /**
     * @param quayRedirectURI the quayRedirectURI to set
     */
    @JsonProperty
    public void setQuayRedirectURI(String quayRedirectURI) {
        this.quayRedirectURI = quayRedirectURI;
    }

    /**
     * @param database the database to set
     */
    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) {
        this.database = database;
    }

    /**
     * @param newHttpClient the httpClient to set
     */
    @JsonProperty("httpClient")
    public void setHttpClientConfiguration(HttpClientConfiguration newHttpClient) {
        this.httpClient = newHttpClient;
    }

    /**
     * @return the githubClientID
     */
    @JsonProperty
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String getGithubClientID() {
        return githubClientID;
    }

    /**
     * @param githubClientID the githubClientID to set
     */
    @JsonProperty
    public void setGithubClientID(String githubClientID) {
        this.githubClientID = githubClientID;
    }

    /**
     * @return the githubRedirectURI
     */
    @JsonProperty
    public String getGithubRedirectURI() {
        return githubRedirectURI;
    }

    /**
     * @param githubRedirectURI the githubRedirectURI to set
     */
    @JsonProperty
    public void setGithubRedirectURI(String githubRedirectURI) {
        this.githubRedirectURI = githubRedirectURI;
    }

    /**
     * @return the githubClientSecret
     */
    @JsonProperty
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public String getGithubClientSecret() {
        return githubClientSecret;
    }

    /**
     * @param githubClientSecret the githubClientSecret to set
     */
    @JsonProperty
    public void setGithubClientSecret(String githubClientSecret) {
        this.githubClientSecret = githubClientSecret;
    }

    /**
     * @return the bitbucketClientID
     */
    @JsonProperty
    public String getBitbucketClientID() {
        return bitbucketClientID;
    }

    /**
     * @param bitbucketClientID the bitbucketClientID to set
     */
    @JsonProperty
    public void setBitbucketClientID(String bitbucketClientID) {
        this.bitbucketClientID = bitbucketClientID;
    }

    /**
     * @return the bitbucketClientSecret
     */
    @JsonProperty
    public String getBitbucketClientSecret() {
        return bitbucketClientSecret;
    }

    /**
     * @param bitbucketClientSecret the bitbucketClientSecret to set
     */
    @JsonProperty
    public void setBitbucketClientSecret(String bitbucketClientSecret) {
        this.bitbucketClientSecret = bitbucketClientSecret;
    }

    public CacheBuilderSpec getAuthenticationCachePolicy() {
        return authenticationCachePolicy;
    }

    public void setAuthenticationCachePolicy(CacheBuilderSpec authenticationCachePolicy) {
        this.authenticationCachePolicy = authenticationCachePolicy;
    }

    public String getGitlabClientID() {
        return gitlabClientID;
    }

    public void setGitlabClientID(String gitlabClientID) {
        this.gitlabClientID = gitlabClientID;
    }

    public String getGitlabRedirectURI() {
        return gitlabRedirectURI;
    }

    public void setGitlabRedirectURI(String gitlabRedirectURI) {
        this.gitlabRedirectURI = gitlabRedirectURI;
    }

    public String getGitlabClientSecret() {
        return gitlabClientSecret;
    }

    public void setGitlabClientSecret(String gitlabClientSecret) {
        this.gitlabClientSecret = gitlabClientSecret;
    }

    public String getZenodoClientID() {
        return zenodoClientID;
    }

    public void setZenodoClientID(String zenodoClientID) {
        this.zenodoClientID = zenodoClientID;
    }

    public String getZenodoRedirectURI() {
        return zenodoRedirectURI;
    }

    public void setZenodoRedirectURI(String zenodoRedirectURI) {
        this.zenodoRedirectURI = zenodoRedirectURI;
    }

    public String getZenodoUrl() {
        return zenodoUrl;
    }

    public void setZenodoUrl(String zenodoUrl) {
        this.zenodoUrl = zenodoUrl;
    }

    public String getZenodoClientSecret() {
        return zenodoClientSecret;
    }

    public void setZenodoClientSecret(String zenodoClientSecret) {
        this.zenodoClientSecret = zenodoClientSecret;
    }

    public String getDiscourseUrl() {
        return discourseUrl;
    }

    public void setDiscourseUrl(String discourseUrl) {
        this.discourseUrl = discourseUrl;
    }

    public String getDiscourseKey() {
        return discourseKey;
    }

    public void setDiscourseKey(String discourseKey) {
        this.discourseKey = discourseKey;
    }

    public Integer getDiscourseCategoryId() {
        return discourseCategoryId;
    }

    public void setDiscourseCategoryId(Integer discourseCategoryId) {
        this.discourseCategoryId = discourseCategoryId;
    }

    public String getGitHubAppId() {
        return gitHubAppId;
    }

    public void setGitHubAppId(String gitHubAppId) {
        this.gitHubAppId = gitHubAppId;
    }

    public String getGitHubAppPrivateKeyFile() {
        return gitHubAppPrivateKeyFile;
    }

    public void setGitHubAppPrivateKeyFile(String gitHubAppPrivateKeyFile) {
        this.gitHubAppPrivateKeyFile = gitHubAppPrivateKeyFile;
    }

    @JsonProperty("esconfiguration")
    public ElasticSearchConfig getEsConfiguration() {
        return esConfiguration;
    }

    public void setEsConfiguration(ElasticSearchConfig esConfiguration) {
        this.esConfiguration = esConfiguration;
    }

    @JsonProperty
    public String getSqsURL() {
        return sqsURL;
    }

    public void setSqsURL(String sqsURL) {
        this.sqsURL = sqsURL;
    }

    @JsonProperty
    public String getGoogleClientID() {
        return googleClientID;
    }

    public void setGoogleClientID(String googleClientID) {
        this.googleClientID = googleClientID;
    }

    @JsonProperty
    public String getGoogleRedirectURI() {
        return googleRedirectURI;
    }

    public void setGoogleRedirectURI(String googleRedirectURI) {
        this.googleRedirectURI = googleRedirectURI;
    }

    @JsonProperty
    public String getGoogleClientSecret() {
        return googleClientSecret;
    }

    public void setGoogleClientSecret(String googleClientSecret) {
        this.googleClientSecret = googleClientSecret;
    }

    @JsonProperty("authorizerType")
    public String getAuthorizerType() {
        return authorizerType;
    }

    public void setAuthorizerType(String authorizerType) {
        this.authorizerType = authorizerType;
    }

    @JsonProperty("samconfiguration")
    public SamConfiguration getSamConfiguration() {
        return samConfiguration;
    }

    public void setSamConfiguration(SamConfiguration samConfiguration) {
        this.samConfiguration = samConfiguration;
    }

    /**
     * A list of a additional Google client ids that Dockstore will accept google tokens from. These ids are in addition
     * to getGoogleClientID, and is intended for any external Google clients that Dockstore will accept tokens from.
     * @return a list of google client ids
     */
    @JsonProperty("externalGoogleClientIdPrefixes")
    public List<String> getExternalGoogleClientIdPrefixes() {
        return externalGoogleClientIdPrefixes;
    }

    public void setExternalGoogleClientIdPrefixes(List<String> externalGoogleClientIdPrefixes) {
        this.externalGoogleClientIdPrefixes = externalGoogleClientIdPrefixes;
    }

    @JsonProperty
    public LimitConfig getLimitConfig() {
        return limitConfig;
    }

    public void setLimitConfig(LimitConfig limitConfig) {
        this.limitConfig = limitConfig;
    }

    @JsonProperty
    public UIConfig getUiConfig() {
        return uiConfig;
    }

    @JsonProperty
    public String getLanguagePluginLocation() {
        return languagePluginLocation;
    }

    public void setLanguagePluginLocation(String languagePluginLocation) {
        this.languagePluginLocation = languagePluginLocation;
    }

    /**
     * This config defines values that define the webservice from the outside world.
     * Most notably, for swagger. But also to configure generated RSS paths and TRS paths
     */
    public static class ExternalConfig {
        @NotEmpty
        private String hostname;

        private String basePath;

        @NotEmpty
        private String scheme;

        private String port;

        private String uiPort = null;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getUiPort() {
            return uiPort;
        }

        public void setUiPort(String uiPort) {
            this.uiPort = uiPort;
        }
    }

    public class ElasticSearchConfig {
        private String hostname;
        private int port;

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class SamConfiguration {
        private String basepath;

        public String getBasepath() {
            return basepath;
        }

        public void setBasepath(String basepath) {
            this.basepath = basepath;
        }
    }

    public static class LimitConfig {
        private Integer workflowLimit;
        private Integer workflowVersionLimit;

        public Integer getWorkflowLimit() {
            return workflowLimit;
        }

        public void setWorkflowLimit(int workflowLimit) {
            this.workflowLimit = workflowLimit;
        }

        public Integer getWorkflowVersionLimit() {
            return workflowVersionLimit;
        }

        public void setWorkflowVersionLimit(int workflowVersionLimit) {
            this.workflowVersionLimit = workflowVersionLimit;
        }
    }

    /**
     * A subset of properties returned to the UI. Only a subset because some properties that will
     * be used by the UI are also used by the web service and predate the existences of this class.
     */
    public static class UIConfig {

        private String dnaStackImportUrl;
        private String dnaNexusImportUrl;
        private String terraImportUrl;

        private String gitHubAuthUrl;
        private String gitHubRedirectPath;
        private String gitHubScope;

        private String quayIoAuthUrl;
        private String quayIoRedirectPath;
        private String quayIoScope;

        private String bitBucketAuthUrl;

        private String gitlabAuthUrl;
        private String gitlabRedirectPath;
        private String gitlabScope;

        private String zenodoAuthUrl;
        private String zenodoRedirectPath;
        private String zenodoScope;

        private String googleScope;

        private String cwlVisualizerUri;

        private String tagManagerId;

        private String gitHubAppInstallationUrl;

        private String documentationUrl;

        private String featuredContentUrl;

        public String getDnaStackImportUrl() {
            return dnaStackImportUrl;
        }

        public void setDnaStackImportUrl(String dnaStackImportUrl) {
            this.dnaStackImportUrl = dnaStackImportUrl;
        }

        public String getDnaNexusImportUrl() {
            return dnaNexusImportUrl;
        }

        public void setDnaNexusImportUrl(String dnaNexusImportUrl) {
            this.dnaNexusImportUrl = dnaNexusImportUrl;
        }

        public String getTerraImportUrl() {
            return terraImportUrl;
        }

        public void setTerraImportUrl(String terraImportUrl) {
            this.terraImportUrl = terraImportUrl;
        }

        public String getGitHubAuthUrl() {
            return gitHubAuthUrl;
        }

        public void setGitHubAuthUrl(String gitHubAuthUrl) {
            this.gitHubAuthUrl = gitHubAuthUrl;
        }

        public String getGitHubRedirectPath() {
            return gitHubRedirectPath;
        }

        public void setGitHubRedirectPath(String gitHubRedirectPath) {
            this.gitHubRedirectPath = gitHubRedirectPath;
        }

        public String getGitHubScope() {
            return gitHubScope;
        }

        public void setGitHubScope(String gitHubScope) {
            this.gitHubScope = gitHubScope;
        }

        public String getQuayIoAuthUrl() {
            return quayIoAuthUrl;
        }

        public void setQuayIoAuthUrl(String quayIoAuthUrl) {
            this.quayIoAuthUrl = quayIoAuthUrl;
        }

        public String getQuayIoRedirectPath() {
            return quayIoRedirectPath;
        }

        public void setQuayIoRedirectPath(String quayIoRedirectPath) {
            this.quayIoRedirectPath = quayIoRedirectPath;
        }

        public String getQuayIoScope() {
            return quayIoScope;
        }

        public void setQuayIoScope(String quayIoScope) {
            this.quayIoScope = quayIoScope;
        }

        public String getBitBucketAuthUrl() {
            return bitBucketAuthUrl;
        }

        public void setBitBucketAuthUrl(String bitBucketAuthUrl) {
            this.bitBucketAuthUrl = bitBucketAuthUrl;
        }

        public String getGitlabAuthUrl() {
            return gitlabAuthUrl;
        }

        public void setGitlabAuthUrl(String gitlabAuthUrl) {
            this.gitlabAuthUrl = gitlabAuthUrl;
        }

        public String getGitlabRedirectPath() {
            return gitlabRedirectPath;
        }

        public void setGitlabRedirectPath(String gitlabRedirectPath) {
            this.gitlabRedirectPath = gitlabRedirectPath;
        }

        public String getGitlabScope() {
            return gitlabScope;
        }

        public void setGitlabScope(String gitlabScope) {
            this.gitlabScope = gitlabScope;
        }


        public String getZenodoAuthUrl() {
            return zenodoAuthUrl;
        }

        public void setZenodoAuthUrl(String zenodoAuthUrl) {
            this.zenodoAuthUrl = zenodoAuthUrl;
        }

        public String getZenodoRedirectPath() {
            return zenodoRedirectPath;
        }

        public void setZenodoRedirectPath(String zenodoRedirectPath) {
            this.zenodoRedirectPath = zenodoRedirectPath;
        }

        public String getZenodoScope() {
            return zenodoScope;
        }

        public void setZenodoScope(String zenodoScope) {
            this.zenodoScope = zenodoScope;
        }


        public String getGoogleScope() {
            return googleScope;
        }

        public void setGoogleScope(String googleScope) {
            this.googleScope = googleScope;
        }

        public String getCwlVisualizerUri() {
            return cwlVisualizerUri;
        }

        public void setCwlVisualizerUri(String cwlVisualizerUri) {
            this.cwlVisualizerUri = cwlVisualizerUri;
        }

        public String getTagManagerId() {
            return tagManagerId;
        }

        public void setTagManagerId(String tagManagerId) {
            this.tagManagerId = tagManagerId;
        }

        public String getGitHubAppInstallationUrl() {
            return gitHubAppInstallationUrl;
        }

        public void setGitHubAppInstallationUrl(String gitHubAppInstallationUrl) {
            this.gitHubAppInstallationUrl = gitHubAppInstallationUrl;
        }

        public String getDocumentationUrl() {
            return documentationUrl;
        }

        public void setDocumentationUrl(String documentationUrl) {
            this.documentationUrl = documentationUrl;
        }

        public String getFeaturedContentUrl() {
            return featuredContentUrl;
        }

        public void setFeaturedContentUrl(String featuredContentUrl) {
            this.featuredContentUrl = featuredContentUrl;
        }
    }
}
