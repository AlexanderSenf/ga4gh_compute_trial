package io.dockstore.common;

/**
 * Class used by registration wizard to for toggle view
 * @author aduncan
 * @since 1.8.0
 */
public class Repository {
    private String organization;
    private String repositoryName;
    private SourceControl gitRegistry;
    private boolean isPresent;
    private boolean canDelete;

    public Repository(String organization, String repositoryName, SourceControl gitRegistry, boolean isPresent, boolean canDelete) {
        this.organization = organization;
        this.repositoryName = repositoryName;
        this.gitRegistry = gitRegistry;
        this.isPresent = isPresent;
        this.canDelete = canDelete;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public SourceControl getGitRegistry() {
        return gitRegistry;
    }

    public void setGitRegistry(SourceControl gitRegistry) {
        this.gitRegistry = gitRegistry;
    }

    public boolean isPresent() {
        return isPresent;
    }

    public void setPresent(boolean present) {
        isPresent = present;
    }

    public String getPath() {
        return String.format("%s/%s", organization, repositoryName);
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public void setCanDelete(boolean canDelete) {
        this.canDelete = canDelete;
    }
}
