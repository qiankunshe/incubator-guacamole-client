/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.guacamole.rest.connection;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.guacamole.GuacamoleClientException;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleSecurityException;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.ConnectionRecord;
import org.apache.guacamole.net.auth.Directory;
import org.apache.guacamole.net.auth.User;
import org.apache.guacamole.net.auth.UserContext;
import org.apache.guacamole.net.auth.permission.ObjectPermission;
import org.apache.guacamole.net.auth.permission.ObjectPermissionSet;
import org.apache.guacamole.net.auth.permission.SystemPermission;
import org.apache.guacamole.net.auth.permission.SystemPermissionSet;
import org.apache.guacamole.GuacamoleSession;
import org.apache.guacamole.rest.ObjectRetrievalService;
import org.apache.guacamole.rest.auth.AuthenticationService;
import org.apache.guacamole.rest.history.APIConnectionRecord;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A REST Service for handling connection CRUD operations.
 * 
 * @author James Muehlner
 */
@Path("/data/{dataSource}/connections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConnectionRESTService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectionRESTService.class);

    /**
     * A service for authenticating users from auth tokens.
     */
    @Inject
    private AuthenticationService authenticationService;
    
    /**
     * Service for convenient retrieval of objects.
     */
    @Inject
    private ObjectRetrievalService retrievalService;
    
    /**
     * Retrieves an individual connection.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection to be retrieved.
     *
     * @param connectionID
     *     The identifier of the connection to retrieve.
     *
     * @return
     *     The connection having the given identifier.
     *
     * @throws GuacamoleException
     *     If an error occurs while retrieving the connection.
     */
    @GET
    @Path("/{connectionID}")
    public APIConnection getConnection(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionID") String connectionID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        
        // Retrieve the requested connection
        return new APIConnection(retrievalService.retrieveConnection(session, authProviderIdentifier, connectionID));

    }

    /**
     * Retrieves the parameters associated with a single connection.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection whose parameters are to be
     *     retrieved.
     *
     * @param connectionID
     *     The identifier of the connection.
     *
     * @return
     *     A map of parameter name/value pairs.
     *
     * @throws GuacamoleException
     *     If an error occurs while retrieving the connection parameters.
     */
    @GET
    @Path("/{connectionID}/parameters")
    public Map<String, String> getConnectionParameters(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionID") String connectionID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);
        User self = userContext.self();

        // Retrieve permission sets
        SystemPermissionSet systemPermissions = self.getSystemPermissions();
        ObjectPermissionSet connectionPermissions = self.getConnectionPermissions();

        // Deny access if adminstrative or update permission is missing
        if (!systemPermissions.hasPermission(SystemPermission.Type.ADMINISTER)
         && !connectionPermissions.hasPermission(ObjectPermission.Type.UPDATE, connectionID))
            throw new GuacamoleSecurityException("Permission to read connection parameters denied.");

        // Retrieve the requested connection
        Connection connection = retrievalService.retrieveConnection(userContext, connectionID);

        // Retrieve connection configuration
        GuacamoleConfiguration config = connection.getConfiguration();

        // Return parameter map
        return config.getParameters();

    }

    /**
     * Retrieves the usage history of a single connection.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection whose history is to be
     *     retrieved.
     *
     * @param connectionID
     *     The identifier of the connection.
     *
     * @return
     *     A list of connection records, describing the start and end times of
     *     various usages of this connection.
     *
     * @throws GuacamoleException
     *     If an error occurs while retrieving the connection history.
     */
    @GET
    @Path("/{connectionID}/history")
    public List<APIConnectionRecord> getConnectionHistory(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionID") String connectionID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);

        // Retrieve the requested connection
        Connection connection = retrievalService.retrieveConnection(session, authProviderIdentifier, connectionID);

        // Retrieve the requested connection's history
        List<APIConnectionRecord> apiRecords = new ArrayList<APIConnectionRecord>();
        for (ConnectionRecord record : connection.getHistory())
            apiRecords.add(new APIConnectionRecord(record));

        // Return the converted history
        return apiRecords;

    }

    /**
     * Deletes an individual connection.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection to be deleted.
     *
     * @param connectionID
     *     The identifier of the connection to delete.
     *
     * @throws GuacamoleException
     *     If an error occurs while deleting the connection.
     */
    @DELETE
    @Path("/{connectionID}")
    public void deleteConnection(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionID") String connectionID)
            throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);

        // Get the connection directory
        Directory<Connection> connectionDirectory = userContext.getConnectionDirectory();

        // Delete the specified connection
        connectionDirectory.remove(connectionID);

    }

    /**
     * Creates a new connection and returns the new connection, with identifier
     * field populated.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext in which the connection is to be created.
     *
     * @param connection
     *     The connection to create.
     *
     * @return
     *     The new connection.
     *
     * @throws GuacamoleException
     *     If an error occurs while creating the connection.
     */
    @POST
    public APIConnection createConnection(@QueryParam("token") String authToken,
            @PathParam("dataSource") String authProviderIdentifier,
            APIConnection connection) throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);
        
        // Validate that connection data was provided
        if (connection == null)
            throw new GuacamoleClientException("Connection JSON must be submitted when creating connections.");

        // Add the new connection
        Directory<Connection> connectionDirectory = userContext.getConnectionDirectory();
        connectionDirectory.add(new APIConnectionWrapper(connection));

        // Return the new connection
        return connection;

    }
  
    /**
     * Updates an existing connection. If the parent identifier of the
     * connection is changed, the connection will also be moved to the new
     * parent group.
     * 
     * @param authToken
     *     The authentication token that is used to authenticate the user
     *     performing the operation.
     *
     * @param authProviderIdentifier
     *     The unique identifier of the AuthenticationProvider associated with
     *     the UserContext containing the connection to be updated.
     *
     * @param connectionID
     *     The identifier of the connection to update.
     *
     * @param connection
     *     The connection data to update the specified connection with.
     *
     * @throws GuacamoleException
     *     If an error occurs while updating the connection.
     */
    @PUT
    @Path("/{connectionID}")
    public void updateConnection(@QueryParam("token") String authToken, 
            @PathParam("dataSource") String authProviderIdentifier,
            @PathParam("connectionID") String connectionID,
            APIConnection connection) throws GuacamoleException {

        GuacamoleSession session = authenticationService.getGuacamoleSession(authToken);
        UserContext userContext = retrievalService.retrieveUserContext(session, authProviderIdentifier);
        
        // Validate that connection data was provided
        if (connection == null)
            throw new GuacamoleClientException("Connection JSON must be submitted when updating connections.");

        // Get the connection directory
        Directory<Connection> connectionDirectory = userContext.getConnectionDirectory();
        
        // Retrieve connection to update
        Connection existingConnection = retrievalService.retrieveConnection(userContext, connectionID);

        // Build updated configuration
        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol(connection.getProtocol());
        config.setParameters(connection.getParameters());

        // Update the connection
        existingConnection.setConfiguration(config);
        existingConnection.setParentIdentifier(connection.getParentIdentifier());
        existingConnection.setName(connection.getName());
        existingConnection.setAttributes(connection.getAttributes());
        connectionDirectory.update(existingConnection);

    }
    
}
