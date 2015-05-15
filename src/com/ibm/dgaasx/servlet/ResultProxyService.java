package com.ibm.dgaasx.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.dgaasx.config.EnvironmentInfo;
import com.ibm.dgaasx.utils.ConnectionUtils;
import com.ibm.rpe.web.service.docgen.api.Parameters;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

@Path("/result")
@Api(value = "/result", description = "A proxy to the Result Service used by DGaaS.")
public class ResultProxyService
{
	private final static Logger log = LoggerFactory.getLogger(ResultProxyService.class);
	
	private static final String CONTENT_DISPOSITION = "Content-Disposition";
	private static final String CONTENT_DISPOSITION_FILENAME = "filename = ";
	
	private Client client = ConnectionUtils.createClient();

	protected boolean checkResponse(ClientResponse response)
	{
		if (Response.Status.Family.SUCCESSFUL != response.getStatusInfo().getFamily())
		{
			log.info( ">>> ERROR: " + response.getStatusInfo().getStatusCode());
			log.info( ">>> Reason phrase: " + response.getStatusInfo().getReasonPhrase());
			log.info( ">>> Details: " + response.getEntity(String.class));
		}
		
		return Response.Status.Family.SUCCESSFUL == response.getStatusInfo().getFamily();
	}
	
	@GET
	@Path( "/{resultID}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@ApiOperation(value = "Download the document produced by DGaaS.", notes = "Download the document produced by DGaaS using the result's URI and the secret token.", response=OutputStream.class, produces="application/octet-stream" )
	@ApiResponses(value = { @ApiResponse(code = 400, message = "Invalid value"), @ApiResponse(code = 404, message = "Result cannot be retrieved. Verify the ID and secret.") })
	public Response result( @ApiParam(value = "The URI of the result as returned by the /job method", required = true)  @PathParam(value="resultID") String resultID, 
							@ApiParam(value = "Job secret as returned by the /docgen method", required = true)   @QueryParam(value="secret") String secret)
	{
		WebResource resultService = client.resource(UriBuilder.fromUri(EnvironmentInfo.getDGaaSURL()).path("/data/files").path( resultID).build());
		
		ClientResponse response = resultService.header(Parameters.Header.SECRET,secret).type(MediaType.APPLICATION_OCTET_STREAM).get(ClientResponse.class); 
		if ( !checkResponse( response))
		{
			return Response.status(Response.Status.NOT_FOUND).entity("Result cannot be retrieved. Verify the ID and secret.").build();
		}
		
		String contentDisposition = response.getHeaders().getFirst( CONTENT_DISPOSITION);
		String fileName = "result";
		if ( contentDisposition != null)
		{
			int pos = contentDisposition.indexOf(CONTENT_DISPOSITION_FILENAME);
			if ( pos >= 0)
			{
				fileName = contentDisposition.substring( pos + CONTENT_DISPOSITION_FILENAME.length());
			}
		}
		
		final InputStream is = response.getEntityInputStream();
		
		// OR: use a custom StreamingOutput and set to Response
		StreamingOutput stream = new StreamingOutput() 
		{
			@Override
			public void write(OutputStream output) throws IOException
			{
				IOUtils.copy(is, output);
			}
		};

		return Response.ok(stream, MediaType.APPLICATION_OCTET_STREAM).header("content-disposition", "attachment; filename = " + fileName).build(); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
}
