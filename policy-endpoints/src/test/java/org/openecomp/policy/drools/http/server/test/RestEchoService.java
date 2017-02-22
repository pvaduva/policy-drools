package org.openecomp.policy.drools.http.server.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/junit/echo")
public class RestEchoService {
	
    @GET
    @Path("{word}")
    @Produces(MediaType.TEXT_PLAIN)
    public String echo(@PathParam("word") String word) {   
    	return word;
    }

}