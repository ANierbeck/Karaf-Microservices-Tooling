package de.nierbeck.javaland.karaf.deployer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONObject;

/**
  *
 */
@Mojo(name = "deploy")
public class SendJsonMojo extends AbstractMojo {

	/**
	 * Optional parameter for another JSON template
	 */
	@Parameter(required=true)
	private String jsonInstall;
	
	@Parameter(required=false, defaultValue="{\n" + 
			"  \"type\":\"EXEC\",\n" + 
			"  \"mbean\":\"org.apache.karaf:type=bundle,name=root\",\n" + 
			"  \"operation\":\"update(java.lang.String)\",\n" + 
			"  \"arguments\":[BUNDLE_ID]\n" + 
			"}")
	private String jsonUpdate;
	
	@Parameter(required=false, defaultValue="{\n" + 
			"  \"type\":\"READ\",\n" + 
			"  \"mbean\":\"org.apache.karaf:type=bundle,name=root\",\n" + 
			"}")
	private String jsonCheck;

	/**
	 * URL pointing to the server to deploy the artefact to
	 */
	@Parameter(required = true)
	private URL url;
	
	@Parameter(defaultValue="${project.groupId}")
	private String groupId;
	
	@Parameter(defaultValue="${project.artifactId}")
	private String artifactId;
	
	@Parameter(defaultValue="${project.version}")
	private String version;
	
	@Parameter(defaultValue="${project.packaging}")
	private String packaging;
	
	@Parameter(defaultValue="${project.classifier}")
	private String classifier;
	
	@Parameter
	private String user;
	
	@Parameter
	private String password;
	
	@Parameter(required=false, defaultValue="false")
	private Boolean skip;
	
	@Parameter(defaultValue="30000")
	private Long timeout;
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(skip)
			return;
		
		if (url == null || jsonInstall == null)
			throw new MojoExecutionException("url and jsonTemplate need to be configured!");
		
		try {
			String bundleId = sendJsonCheckCommand();
			if (bundleId == null) {
				sendJsonInstallCommand();
			} else {
				sendJsonUpdateCommand(bundleId);
			}
			
		} catch (ClientProtocolException e) {
			throw new MojoExecutionException("Failed: ", e);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed: ", e);
		} catch (AuthenticationException e) {
			throw new MojoExecutionException("Failed: ", e);
		}
	}
	
	private void sendJsonUpdateCommand(String bundleId) throws AuthenticationException, UnsupportedEncodingException, ClientProtocolException, MojoFailureException, IOException {
		
		int indexOf = jsonUpdate.indexOf("BUNDLE_ID");
		
		String json = jsonUpdate;
		
		if (indexOf > -1) {
			json = jsonUpdate.replace("BUNDLE_ID", bundleId);
		}
		
		String returnString = jsonCommunicate(json);
		
		JSONObject jsonObject = new JSONObject(returnString);
		int status = jsonObject.getInt("status");
		
		if (status != 200) {
			throw new MojoFailureException("Server responded with status code: "+status+"\n"+json);
		}
	}

	private String sendJsonCheckCommand() throws AuthenticationException, ClientProtocolException, MojoFailureException, IOException {
		
		String json = jsonCommunicate(jsonCheck);
		
		JSONObject jsonObject = new JSONObject(json);
		JSONObject value = jsonObject.getJSONObject("value");
		JSONObject bundles = value.getJSONObject("Bundles");
		Set<String> bundleNames = bundles.keySet();
		
		String osgiVersion = version.replace("-", ".");
		
		
		for (String bundleId : bundleNames) {
			JSONObject bundle = bundles.getJSONObject(bundleId);
			String name = bundle.getString("Name");
			String bundleVersion = bundle.getString("Version");
			if ((groupId+"."+artifactId).equalsIgnoreCase(name) && osgiVersion.equalsIgnoreCase(bundleVersion)) {
				return bundleId;
			}
		}
		
		return null;
	}

	public CloseableHttpClient createHttpClient() {
		PlainConnectionSocketFactory plainsf = PlainConnectionSocketFactory
				.getSocketFactory();
		
		Registry<ConnectionSocketFactory> rb = RegistryBuilder
				.<ConnectionSocketFactory> create().register("http", plainsf).build();
		
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(rb);
		
		return HttpClients.custom().setConnectionManager(cm).build();
	}

	private void sendJsonInstallCommand() throws ClientProtocolException, IOException, AuthenticationException, MojoFailureException, MojoExecutionException {

		String json = jsonCommunicate(jsonInstall);
		JSONObject jsonObject = new JSONObject(json);
		int status = jsonObject.getInt("status");
		
		if (status != 200) {
			throw new MojoFailureException("Server responded with status code: "+status+"\n"+json);
		}

	}

	private String jsonCommunicate(String json) throws UnsupportedEncodingException, AuthenticationException, IOException,
			ClientProtocolException, MojoFailureException {
		CloseableHttpClient httpClient = createHttpClient();
		
		HttpPost post = new HttpPost(url.toString());
		StringEntity input = new StringEntity(json);
		post.setEntity(input);
		post.addHeader("Accept-Language", "en-us;q=0.8,en;q=0.5");

		if (user != null && password != null) {
		
		UsernamePasswordCredentials creds = new UsernamePasswordCredentials(user, password);
			// Create AuthCache instance
			AuthCache authCache = new BasicAuthCache();
			// Generate BASIC scheme object and add it to the local auth cache
			BasicScheme basicAuth = new BasicScheme();
			authCache.put(getHttpHost(url.toString()), basicAuth);
	
			BasicHttpContext localcontext = new BasicHttpContext();
			
			localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);
			post.addHeader(basicAuth.authenticate(creds, post, localcontext));
		}
		
		CloseableHttpResponse response = httpClient.execute(post, HttpClientContext.create());

		int statusCode = response.getStatusLine().getStatusCode();
		String responseBodyAsString = EntityUtils.toString(response.getEntity());
		
		if (statusCode != 200) {
			throw new MojoFailureException("Server responded with status code: "+statusCode+"\n"+responseBodyAsString);
		}
		
		response.close();
		httpClient.close();
		
		return responseBodyAsString;
	}

	private String replacement(String toReplace) throws MojoExecutionException {
		if("groupId".equalsIgnoreCase(toReplace)) {
			return groupId;
		} else if("artifactId".equalsIgnoreCase(toReplace)) {
			return artifactId;
		} else if("version".equalsIgnoreCase(toReplace)) {
			return version;
		} else if("packaging".equalsIgnoreCase(toReplace)) {
			return packaging;
		} else if("classifier".equalsIgnoreCase(toReplace)) {
			return classifier;
		} else { 
			throw new MojoExecutionException("Can't find replacing variable! "+toReplace);
		}
	}
		
	
	private HttpHost getHttpHost(String path) {
		int schemeSeperator = path.indexOf(":");
		String scheme = path.substring(0, schemeSeperator);

		int portSeperator = path.lastIndexOf(":");
		String hostname = path.substring(schemeSeperator + 3, portSeperator);

		int port = Integer.parseInt(path.substring(portSeperator + 1,
				portSeperator + 5));

		HttpHost targetHost = new HttpHost(hostname, port, scheme);
		return targetHost;
	}

}