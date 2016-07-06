package com.versioneye;

import com.versioneye.dto.ProjectJsonResponse;
import com.versioneye.utils.HttpUtils;
import com.versioneye.utils.PropertiesUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.util.Properties;

/**
 * Creates a project at VersionEye based on the dependencies from the current project.
 */
@Mojo( name = "create", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class CreateMojo extends ProjectMojo {

    @Parameter( property = "resource", defaultValue = "/projects?api_key=")
    private String resource;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            setProxy();
            prettyPrintStart();

            ByteArrayOutputStream jsonDependenciesStream = null;
            if (transitiveDependencies == true){
                jsonDependenciesStream = getTransitiveDependenciesJsonStream(nameStrategy);
            } else {
                jsonDependenciesStream = getDirectDependenciesJsonStream(nameStrategy);
            }

            if (jsonDependenciesStream == null){
                prettyPrint0End();
                return ;
            }

            ProjectJsonResponse response = uploadDependencies(jsonDependenciesStream);
            merge(response.getId());
            if (updatePropertiesAfterCreate) {
                writeProperties( response );
            }
            prettyPrint(response);
        } catch( Exception exception ){
            throw new MojoExecutionException("Oh no! Something went wrong :-( " +
                    "Get in touch with the VersionEye guys and give them feedback." +
                    "You find them on Twitter at https//twitter.com/VersionEye. ", exception);
        }
    }

    private ProjectJsonResponse uploadDependencies(ByteArrayOutputStream outStream) throws Exception {
        String apiKey = fetchApiKey();
        String url = baseUrl + apiPath + resource + apiKey;
        Reader reader = HttpUtils.post(url, outStream.toByteArray(), "upload", visibility, name, organisation, team);
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(reader, ProjectJsonResponse.class );
    }

    private void prettyPrintStart(){
        getLog().info(".");
        getLog().info("Starting to upload dependencies. This can take a couple seconds ... ");
        getLog().info(".");
    }

    protected void writeProperties(ProjectJsonResponse response) throws Exception {
        Properties properties = fetchProjectProperties();
        if (response.getId() != null) {
            properties.setProperty("project_id", response.getId());
        }
        PropertiesUtils utils = new PropertiesUtils();
        utils.writeProperties(properties, getPropertiesPath());
    }

    protected void merge(String childId) {
        if (mergeAfterCreate == false) {
            return ;
        }
        try {

            if (parentGroupId == null || parentGroupId.isEmpty() ||
                    parentArtifactId == null || parentArtifactId.isEmpty()){
                MavenProject mp = project.getParent();
                if (mp == null || mp.getGroupId() == null || mp.getGroupId().isEmpty() ||
                        mp.getArtifactId() == null || mp.getArtifactId().isEmpty()){
                    return ;
                }
                parentGroupId = mp.getGroupId();
                parentArtifactId = mp.getArtifactId();
            }

            parentGroupId = parentGroupId.replaceAll("\\.", "~").replaceAll("/", ":");
            parentArtifactId = parentArtifactId.replaceAll("\\.", "~").replaceAll("/", ":");

            if (project.getGroupId().equals(parentGroupId) && project.getArtifactId().equals(parentArtifactId)){
                return ;
            }

            getLog().debug("group: " + parentGroupId + " artifact: " + parentArtifactId);
            String url = baseUrl + apiPath + "/projects/" + parentGroupId + "/" + parentArtifactId + "/merge_ga/" + childId + "?api_key=" + fetchApiKey();

            String response = HttpUtils.get(url);
            getLog().debug("merge response: " + response);
        } catch (Exception ex) {
            getLog().error(ex);
        }
    }

}
