package ru.concerteza.util.buildnumber;

/**
 * User: alexey Date: 11/16/11
 */
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Goal which creates build number.
 *
 * @threadSafe
 * @goal extract-buildnumber
 * @phase prepare-package
 */
public class JGitBuildNumberMojo extends AbstractMojo {

    /**
     * Revision property name
     *
     * @parameter expression="${revisionProperty}"
     */
    private String revisionProperty = "git.revision";

    /**
     * Branch property name
     *
     * @parameter expression="${branchProperty}"
     */
    private String branchProperty = "git.branch";

    /**
     * Tag property name
     *
     * @parameter expression="${tagProperty}"
     */
    private String tagProperty = "git.tag";

    /**
     * Commits count property name
     *
     * @parameter expression="${commitsCountProperty}"
     */
    private String commitsCountProperty = "git.commitsCount";

    /**
     * Buildnumber property name
     *
     * @parameter expression="${buildnumberProperty}"
     */
    private String buildnumberProperty = "git.buildnumber";

    /**
     * Java Script buildnumber callback
     *
     * @parameter expression="${javaScriptBuildnumberCallback}"
     */
    private String javaScriptBuildnumberCallback = null;

    /**
     * Directory to start searching git root from, should contain '.git' directory or be a subdirectory of such directory.
     * '${project.basedir}' is used by default.
     *
     * @parameter expression="${repositoryDirectory}" default-value="${project.basedir}"
     */
    private File repositoryDirectory;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;

    private static final Properties propertiesIncludingGitVersion = new Properties();

    /**
     * Extracts buildnumber fields from git repository and publishes them as maven properties. Executes only once per build. Return default
     * (unknown) buildnumber fields on error.
     *
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Properties props = project.getProperties();
        try {
            synchronized (propertiesIncludingGitVersion) {
                if (propertiesIncludingGitVersion.isEmpty()) {
                    extractGitInformation(props);
                    propertiesIncludingGitVersion.putAll(props);

                    return;
                }
            }

            String revision = propertiesIncludingGitVersion.getProperty(revisionProperty);
            if (null == revision) {
                // we are in subproject, but parent project wasn't build this time,
                // maybe build is running from parent with custom module list - 'pl' argument
                getLog().info("Cannot extract Git info, maybe custom build with 'pl' argument is running");
                fillPropsUnknown(props);
                return;
            }
            props.setProperty(revisionProperty, revision);
            props.setProperty(branchProperty, propertiesIncludingGitVersion.getProperty(branchProperty));
            props.setProperty(tagProperty, propertiesIncludingGitVersion.getProperty(tagProperty));
            props.setProperty(commitsCountProperty, propertiesIncludingGitVersion.getProperty(commitsCountProperty));
            props.setProperty(buildnumberProperty, propertiesIncludingGitVersion.getProperty(buildnumberProperty));
        } catch (Exception ex) {
            getLog().error(ex);
            fillPropsUnknown(props);
        }
    }

    private void fillPropsUnknown(Properties props) {
        props.setProperty(revisionProperty, "UNKNOWN_REVISION");
        props.setProperty(branchProperty, "UNKNOWN_BRANCH");
        props.setProperty(tagProperty, "UNKNOWN_TAG");
        props.setProperty(commitsCountProperty, "-1");
        props.setProperty(buildnumberProperty, "UNKNOWN_BUILDNUMBER");
    }

    private String createBuildnumber(BuildNumber bn) throws ScriptException {
        if (null != javaScriptBuildnumberCallback) {
            return buildnumberFromJS(bn);
        }
        return bn.defaultBuildnumber();
    }

    private String buildnumberFromJS(BuildNumber bn) throws ScriptException {
        ScriptEngine jsEngine = new ScriptEngineManager().getEngineByName("JavaScript");
        jsEngine.put("tag", bn.getTag());
        jsEngine.put("branch", bn.getBranch());
        jsEngine.put("revision", bn.getRevision());
        jsEngine.put("shortRevision", bn.getShortRevision());
        jsEngine.put("commitsCount", bn.getCommitsCount());
        Object res = jsEngine.eval(javaScriptBuildnumberCallback);
        if (null == res) {
            throw new IllegalStateException("JS buildnumber callback returns null");
        }
        return res.toString();
    }

    private void extractGitInformation(Properties props) throws IOException, ScriptException {
        // build started from this projects root
        BuildNumber bn = BuildNumberExtractor.extract(repositoryDirectory);
        props.setProperty(revisionProperty, bn.getRevision());
        props.setProperty(branchProperty, bn.getBranch());
        props.setProperty(tagProperty, bn.getTag());
        props.setProperty(commitsCountProperty, bn.getCommitsCountAsString());
        // create composite buildnumber
        String composite = createBuildnumber(bn);
        props.setProperty(buildnumberProperty, composite);
        getLog().info("Git info extracted, revision: '" + bn.getShortRevision() + "', branch: '" + bn.getBranch()
                + "', tag: '" + bn.getTag() + "', commitsCount: '" + bn.getCommitsCount() + "', buildnumber: '" + composite + "'");
    }
}
